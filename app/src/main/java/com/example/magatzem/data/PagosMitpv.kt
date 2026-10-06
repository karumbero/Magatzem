package com.example.magatzem.data

/**
 * Enlace entre los pagos a proveedor hechos en efectivo desde MiTPV (con proveedor y número de factura
 * tecleados por el cajero) y las facturas de Magatzem. Funciona en los dos sentidos, porque el pago y la
 * factura pueden llegar en cualquier orden:
 * - al importar un cierre se busca la factura de cada pago ([aplicarRetirada]);
 * - al aparecer una factura (entrada con factura, o albarán pasado a factura) se buscan sus pagos
 *   pendientes de aplicar ([aplicarAFactura]).
 * Un pago aplicado crea un pago "Contado" ya pagado, con la fecha del pago de MiTPV, y la factura pasa a
 * pagada cuando todos sus pagos tienen fecha y suman su total. Todas las funciones esperan estar dentro de
 * una transacción abierta por quien las llama.
 */
object PagosMitpv {
    fun normalizar(numero: String?): String = numero.orEmpty().filter { !it.isWhitespace() }.uppercase()

    /** Pagada = tiene pagos, todos con fecha, y suman al menos el total de la factura. */
    suspend fun recalcularEstado(db: AppDatabase, facturaId: Long) {
        val recepcionDao = db.recepcionDao()
        val pagos = db.pagoFacturaDao().obtenerPorFactura(facturaId)
        val factura = recepcionDao.obtenerFactura(facturaId) ?: return
        val total = factura.base + factura.iva
        val pagada = pagos.isNotEmpty() && pagos.all { it.fechaPago != null } && pagos.sumOf { it.importe } >= total - 0.005
        val estado = if (pagada) ESTADO_FACTURA_PAGADA else ESTADO_FACTURA_PENDIENTE
        if (factura.estado != estado) recepcionDao.actualizarFactura(factura.copy(estado = estado))
    }

    private suspend fun proveedorUuidDe(db: AppDatabase, factura: FacturaEntity): String? {
        val recepcion = factura.recepcionId?.let { db.recepcionDao().obtenerRecepcion(it) } ?: return null
        return db.proveedorDao().obtenerTodos().firstOrNull { it.id == recepcion.proveedorId }?.uuid
    }

    /** "2026-10-01 20:20:18" → "2026-10-01" (las fechas de documentos se guardan en ISO). */
    private fun fechaDocumento(iso: String): String = iso.take(10)

    private suspend fun registrarPago(db: AppDatabase, factura: FacturaEntity, retirada: RetiradaImportadaEntity) {
        val contado = db.formaPagoDao().obtenerTodas().firstOrNull { it.nombre == NOMBRE_CONTADO }
        val previos = db.pagoFacturaDao().obtenerPorFactura(factura.id).size
        val fecha = fechaDocumento(retirada.fecha)
        val pago = PagoFacturaEntity(
            facturaId = factura.id, formaPagoId = contado?.id, numeroPlazo = previos + 1, totalPlazos = previos + 1,
            importe = retirada.importe, fechaPrevista = fecha, fechaPago = fecha
        )
        val id = db.pagoFacturaDao().insertarUno(pago)
        db.cierreImportadoDao().marcarRetiradaAplicada(retirada.id, id)
        recalcularEstado(db, factura.id)
    }

    /** Un pago de MiTPV acaba de llegar: si ya existe su factura (mismo proveedor y número), se aplica. */
    suspend fun aplicarRetirada(db: AppDatabase, retirada: RetiradaImportadaEntity): Boolean {
        val numero = normalizar(retirada.numeroFactura)
        if (retirada.proveedorUuid == null || numero.isEmpty()) return false
        val factura = db.recepcionDao().obtenerFacturas().firstOrNull {
            normalizar(it.numero) == numero && proveedorUuidDe(db, it) == retirada.proveedorUuid
        } ?: return false
        registrarPago(db, factura, retirada)
        return true
    }

    /** Una factura acaba de crearse: se le aplican los pagos de MiTPV que la estaban esperando. */
    suspend fun aplicarAFactura(db: AppDatabase, factura: FacturaEntity): Int {
        val numero = normalizar(factura.numero)
        val proveedorUuid = proveedorUuidDe(db, factura)
        if (numero.isEmpty() || proveedorUuid == null) return 0
        var aplicados = 0
        for (r in db.cierreImportadoDao().retiradasProveedorSinAplicar()) {
            if (r.proveedorUuid == proveedorUuid && normalizar(r.numeroFactura) == numero) {
                registrarPago(db, factura, r)
                aplicados++
            }
        }
        return aplicados
    }
}
