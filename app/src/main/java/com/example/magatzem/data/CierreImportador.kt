package com.example.magatzem.data

import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONException
import org.json.JSONObject

class FormatoCierreException(mensaje: String) : Exception(mensaje)

/** Qué ha pasado al importar un cierre (para el aviso al usuario). */
data class ResultadoCierre(
    val fechaCierre: String,
    val tickets: Int,
    val unidadesVendidas: Int,
    val unidadesDevueltas: Int,
    /** Líneas cuyo producto ya no existe aquí (SKU/descripción): se guardan pendientes, sin tocar existencias. */
    val productosDesconocidos: List<String>,
    /** Artículos que han quedado con existencia negativa tras descontar las ventas. */
    val existenciasNegativas: List<String>,
    /** Banco que recibió el dinero y cuánto: cobros con tarjeta + ingresos de caja. */
    val bancoNombre: String?,
    val tarjetaABanco: Double,
    val ingresosABanco: Double,
    /** Efectivo pagado a proveedores desde la caja (no pasa por el banco). */
    val pagosProveedor: Double,
    /** Facturas que ya estaban en Magatzem y se han marcado pagadas con estos pagos (el resto espera a su factura). */
    val facturasPagadas: Int,
    /** Saldo del efectivo de la tienda tras este cierre. */
    val saldoCaja: Double
)

/**
 * Importa un cierre de caja de MiTPV (formato `mitpv-cierre` v1, ver `CierreCajaExportador` en MiTPV).
 * Magatzem lleva el stock: por cada línea descuenta (o, en una devolución, repone) la existencia del
 * artículo, y guarda el cierre, sus retiradas a banco, los tickets y las líneas con el **precio
 * realmente cobrado** y el coste del artículo en ese momento (capa 0). Además:
 * - el total cobrado con tarjeta y cada ingreso de caja a banco entran en el **banco principal**
 *   (ver [FormaPagoEntity.principal]; con un solo banco es ese);
 * - la **caja** (efectivo) suma las ventas al contado y resta los ingresos a banco y los pagos a proveedor
 *   (salidas de caja que MiTPV marca como PROVEEDOR), de modo que su saldo
 *   queda igual al efectivo que MiTPV arrastra al día siguiente (el esperado; el descuadre no lo cambia).
 * Todo en una transacción, y el uuid del cierre se rechaza si ya se importó. Nunca toca
 * `fechaActualizacion` de los artículos.
 */
class CierreImportador(private val db: AppDatabase) {
    suspend fun importar(texto: String, nombreArchivo: String?): ResultadoCierre {
        val raiz = try {
            JSONObject(texto)
        } catch (e: Exception) {
            throw FormatoCierreException("El fichero no es un JSON válido")
        }
        if (raiz.optString("formato") != FORMATO) throw FormatoCierreException("No es un fichero de cierre de caja de MiTPV")
        if (raiz.optInt("version") != VERSION) throw FormatoCierreException("Versión de fichero no soportada: ${raiz.optInt("version")}")
        try {
            return importarValidado(raiz, nombreArchivo)
        } catch (e: JSONException) {
            throw FormatoCierreException("Fichero incompleto o con campos incorrectos: ${e.message}")
        }
    }

    private suspend fun importarValidado(raiz: JSONObject, nombreArchivo: String?): ResultadoCierre {
        val dao = db.cierreImportadoDao()
        val productoDao = db.productoDao()
        val uuid = raiz.getString("uuid")
        dao.obtenerPorUuid(uuid)?.let {
            throw FormatoCierreException("Este cierre (${raiz.getString("cerradoEn")}) ya se importó el ${it.importadoEn}.")
        }
        val caja = raiz.getJSONObject("caja")
        // Sin banco al que llevar tarjeta e ingresos no se importa nada (se avisa y se puede reenviar).
        // Un cierre sin tarjeta ni traspasos a banco (solo pagos a proveedor) no lo necesita.
        val retiradasJson = raiz.getJSONArray("retiradas")
        val hayTraspasos = (0 until retiradasJson.length()).any { retiradasJson.getJSONObject(it).optString("tipo", TIPO_BANCO) != TIPO_PROVEEDOR }
        val banco: FormaPagoEntity? = if (caja.getDouble("ventaTarjeta") != 0.0 || hayTraspasos) bancoParaCobros() else null
        val tickets = raiz.getJSONArray("tickets")
        val ahora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // Seguridad extra: ningún ticket del fichero puede estar ya importado en otro cierre.
        val uuidsTickets = List(tickets.length()) { tickets.getJSONObject(it).getString("uuid") }
        if (uuidsTickets.isNotEmpty() && dao.contarVentasPorUuid(uuidsTickets) > 0) {
            throw FormatoCierreException("Algún ticket de este cierre ya estaba importado en otro cierre.")
        }

        return db.withTransaction {
            guardarCajeros(raiz, db)
            val cierreId = dao.insertarCierre(
                CierreImportadoEntity(
                    uuid = uuid,
                    origen = raiz.getString("origen"),
                    abiertoEn = raiz.optString("abiertoEn").takeIf { it.isNotBlank() && it != "null" },
                    cerradoEn = raiz.getString("cerradoEn"),
                    cajeroNombre = raiz.getJSONObject("cajero").getString("nombre"),
                    saldoInicial = caja.getDouble("saldoInicial"),
                    ventaContadoTickets = caja.getDouble("ventaContadoTickets"),
                    ventaTarjetaTickets = caja.getDouble("ventaTarjetaTickets"),
                    totalDatafono = caja.getDouble("totalDatafono"),
                    ajusteTarjeta = caja.getDouble("ajusteTarjeta"),
                    ventaContado = caja.getDouble("ventaContado"),
                    ventaTarjeta = caja.getDouble("ventaTarjeta"),
                    // Solo lo que fue a banco (los pagos a proveedor se guardan aparte, ver abajo).
                    retiradoBanco = (0 until retiradasJson.length()).map { retiradasJson.getJSONObject(it) }
                        .filter { it.optString("tipo", TIPO_BANCO) != TIPO_PROVEEDOR }.sumOf { it.getDouble("importe") },
                    efectivoEsperado = caja.getDouble("efectivoEsperado"),
                    efectivoContado = caja.getDouble("efectivoContado"),
                    diferencia = caja.getDouble("diferencia"),
                    numTickets = caja.getInt("numTickets"),
                    primerTicket = if (caja.isNull("primerTicket")) null else caja.getInt("primerTicket"),
                    ultimoTicket = if (caja.isNull("ultimoTicket")) null else caja.getInt("ultimoTicket"),
                    nombreArchivo = nombreArchivo,
                    importadoEn = ahora
                )
            )
            val retiradas = raiz.getJSONArray("retiradas")
            val retiradasGuardadas = mutableListOf<RetiradaImportadaEntity>()
            for (i in 0 until retiradas.length()) {
                val r = retiradas.getJSONObject(i)
                val entidad = RetiradaImportadaEntity(
                    uuid = r.getString("uuid"), cierreId = cierreId,
                    fecha = r.getString("fecha"), importe = r.getDouble("importe"),
                    tipo = r.optString("tipo", TIPO_BANCO).ifBlank { TIPO_BANCO },
                    nota = if (r.isNull("nota")) null else r.optString("nota").ifBlank { null },
                    proveedorUuid = if (r.isNull("proveedorUuid")) null else r.optString("proveedorUuid").ifBlank { null },
                    numeroFactura = if (r.isNull("factura")) null else r.optString("factura").ifBlank { null }
                )
                retiradasGuardadas += entidad.copy(id = dao.insertarRetirada(entidad))
            }

            // Uuids de proveedor y categoría para guardarlos junto a cada línea (ver VentaLineaImportadaEntity).
            val proveedoresDb = db.proveedorDao().obtenerTodos()
            val proveedorUuidPorId = proveedoresDb.associate { it.id to it.uuid }
            val proveedoresExentos = proveedoresDb.filter { it.exentoIva }.map { it.id }.toSet()
            val categoriaUuidPorId = db.categoriaDao().obtenerTodas().associate { it.id to it.uuid }
            var vendidas = 0
            var devueltas = 0
            val desconocidos = mutableListOf<String>()
            val tocados = linkedSetOf<Long>()
            for (i in 0 until tickets.length()) {
                val t = tickets.getJSONObject(i)
                val ventaId = dao.insertarVenta(
                    VentaImportadaEntity(
                        uuid = t.getString("uuid"), cierreId = cierreId, numero = t.getInt("numero"),
                        fecha = t.getString("fecha"), formaPago = t.getString("formaPago"),
                        importeIva = t.getDouble("importeIva"), importeTotal = t.getDouble("importeTotal"),
                        ventaOriginalUuid = if (t.isNull("ventaOriginalUuid")) null else t.getString("ventaOriginalUuid"),
                        cajeroUuid = if (t.isNull("cajeroUuid")) null else t.optString("cajeroUuid").ifBlank { null }
                    )
                )
                val lineas = t.getJSONArray("lineas")
                val entidades = mutableListOf<VentaLineaImportadaEntity>()
                for (j in 0 until lineas.length()) {
                    val l = lineas.getJSONObject(j)
                    val productoUuid = if (l.isNull("productoUuid")) null else l.getString("productoUuid")
                    val producto = productoUuid?.let { productoDao.obtenerPorUuid(it) }
                    val cantidad = l.getInt("cantidad")
                    val sku = if (l.isNull("sku")) null else l.getString("sku")
                    if (producto != null) {
                        // Venta: cantidad positiva resta; devolución: cantidad negativa repone.
                        productoDao.sumarExistencia(producto.id, -cantidad)
                        tocados += producto.id
                    } else {
                        desconocidos += (sku ?: l.getString("descripcion"))
                    }
                    if (cantidad > 0) vendidas += cantidad else devueltas += -cantidad
                    entidades += VentaLineaImportadaEntity(
                        uuid = l.getString("uuid"), ventaId = ventaId, productoId = producto?.id,
                        productoUuid = productoUuid, sku = sku,
                        codigoBarras = if (l.isNull("codigoBarras")) null else l.getString("codigoBarras"),
                        descripcion = l.getString("descripcion"), cantidad = cantidad,
                        precioUnitarioConIva = l.getDouble("precioUnitarioConIva"),
                        ivaPorcentaje = l.getDouble("ivaPorcentaje"), subtotalConIva = l.getDouble("subtotalConIva"),
                        // Precio coste = base + IVA + recargo de equivalencia; beneficio = PVP cobrado − precio coste.
                        costeUnitario = producto?.coste?.let { it * factorCoste(producto.proveedorId in proveedoresExentos) },
                        proveedorUuid = producto?.proveedorId?.let { proveedorUuidPorId[it] },
                        categoriaUuid = producto?.categoriaId?.let { categoriaUuidPorId[it] }
                    )
                }
                dao.insertarLineas(entidades)
            }

            // --- Caja y banco ---
            val movimientos = db.movimientoDao()
            val cerradoEn = raiz.getString("cerradoEn")
            val saldoInicial = caja.getDouble("saldoInicial")
            val saldoPrevio = movimientos.saldoCaja()
            if (saldoPrevio == null) {
                if (saldoInicial != 0.0) movimientos.insertarCaja(MovimientoCajaEntity(fecha = cerradoEn, concepto = "Saldo inicial", importe = saldoInicial, cierreId = cierreId))
            } else if (kotlin.math.abs(saldoPrevio - saldoInicial) > 0.005) {
                // El cierre empezó con otro saldo del que Magatzem tenía (falta algún cierre, o se tecleó a mano).
                movimientos.insertarCaja(MovimientoCajaEntity(fecha = cerradoEn, concepto = "Ajuste al saldo inicial de MiTPV", importe = saldoInicial - saldoPrevio, cierreId = cierreId))
            }
            val ventaContado = caja.getDouble("ventaContado")
            if (ventaContado != 0.0) movimientos.insertarCaja(MovimientoCajaEntity(fecha = cerradoEn, concepto = "Ventas al contado (cierre)", importe = ventaContado, cierreId = cierreId))
            var ingresos = 0.0
            var pagosProveedor = 0.0
            var facturasPagadas = 0
            for (i in 0 until retiradas.length()) {
                val r = retiradas.getJSONObject(i)
                val importe = r.getDouble("importe")
                if (r.optString("tipo", TIPO_BANCO) == TIPO_PROVEEDOR) {
                    // Pago al contado a un proveedor: solo sale de la caja (la factura se marca pagada en Oficina → Facturas).
                    pagosProveedor += importe
                    val guardada = retiradasGuardadas[i]
                    val proveedor = guardada.proveedorUuid?.let { u -> db.proveedorDao().obtenerTodos().firstOrNull { it.uuid == u }?.nombre }
                    val detalle = listOfNotNull(proveedor, guardada.numeroFactura?.let { "factura $it" }, guardada.nota).joinToString(" · ")
                    movimientos.insertarCaja(MovimientoCajaEntity(fecha = r.getString("fecha"), concepto = "Pago a proveedor al contado" + if (detalle.isNotBlank()) " ($detalle)" else "", importe = -importe, cierreId = cierreId))
                    // Si la factura ya está en Magatzem se marca pagada con la fecha de este pago; si no, queda esperándola.
                    if (PagosMitpv.aplicarRetirada(db, guardada)) facturasPagadas++
                } else {
                    ingresos += importe
                    movimientos.insertarCaja(MovimientoCajaEntity(fecha = r.getString("fecha"), concepto = "Ingreso a banco ${banco!!.nombre}", importe = -importe, cierreId = cierreId))
                    movimientos.insertarBanco(MovimientoBancoEntity(bancoId = banco.id, fecha = r.getString("fecha"), concepto = "Ingreso de efectivo desde caja", importe = importe, cierreId = cierreId))
                }
            }
            // El descuadre del arqueo NO entra en la caja: MiTPV arrastra al día siguiente el efectivo esperado
            // (saldo inicial + ventas - ingresos), así que la caja de Magatzem lo imita. El descuadre queda
            // registrado en el propio cierre importado.
            val tarjeta = caja.getDouble("ventaTarjeta")
            if (tarjeta != 0.0) movimientos.insertarBanco(MovimientoBancoEntity(bancoId = banco!!.id, fecha = cerradoEn, concepto = "Cobros con tarjeta (cierre)", importe = tarjeta, cierreId = cierreId))
            val saldoCaja = movimientos.saldoCaja() ?: 0.0

            val negativas = tocados.mapNotNull { id -> productoDao.obtenerPorId(id) }
                .filter { it.existencia < 0 }.map { (it.sku ?: it.nombre) + " (${it.existencia})" }
            ResultadoCierre(cerradoEn, tickets.length(), vendidas, devueltas, desconocidos.distinct(), negativas, banco?.nombre, tarjeta, ingresos, pagosProveedor, facturasPagadas, saldoCaja)
        }
    }

    /**
     * El banco que recibe tarjeta e ingresos: el marcado como principal o, si solo hay uno activo, ese.
     * Si no hay ninguno o hay varios sin marcar, no se puede importar.
     */
    private suspend fun bancoParaCobros(): FormaPagoEntity {
        val bancos = db.formaPagoDao().obtenerTodas().filter { it.esBanco() && it.activo }
        return bancos.firstOrNull { it.principal }
            ?: bancos.singleOrNull()
            ?: throw FormatoCierreException(
                if (bancos.isEmpty()) "No hay ninguna cuenta bancaria. Añade el banco en Ajustes → Formas de pago y vuelve a importar el cierre."
                else "Hay varios bancos y ninguno marcado como principal. Márcalo en Ajustes → Bancos y vuelve a importar el cierre."
            )
    }

    companion object {
        const val TIPO_BANCO = "BANCO"
        const val TIPO_PROVEEDOR = "PROVEEDOR"
        const val FORMATO = "mitpv-cierre"
        const val VERSION = 1
    }
}

/** Guarda/actualiza los cajeros del cierre: lista `cajeros` (todos los del turno) y `cajero` (el del cierre, ficheros antiguos). */
private suspend fun guardarCajeros(raiz: JSONObject, db: AppDatabase) {
    val dao = db.cajeroDao()
    val fecha = raiz.getString("cerradoEn")
    val vistos = mutableListOf<Pair<String, String>>()
    raiz.optJSONArray("cajeros")?.let { a ->
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            vistos += o.getString("uuid") to o.getString("nombre")
        }
    }
    raiz.optJSONObject("cajero")?.let { c ->
        val u = c.optString("uuid"); if (!c.isNull("uuid") && u.isNotBlank()) vistos += u to c.getString("nombre")
    }
    for ((u, n) in vistos) {
        val actual = dao.obtenerPorUuid(u)
        dao.guardar((actual ?: CajeroEntity(uuid = u, nombre = n, ultimaActividad = fecha)).copy(nombre = n, ultimaActividad = fecha))
    }
}
