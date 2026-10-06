package com.example.magatzem.ui.oficina

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.AlbaranEntity
import com.example.magatzem.data.ESTADO_FACTURA_PAGADA
import com.example.magatzem.data.ESTADO_FACTURA_PENDIENTE
import com.example.magatzem.data.FacturaEntity
import com.example.magatzem.data.FormaPagoEntity
import com.example.magatzem.data.factorCoste
import com.example.magatzem.data.MovimientoBancoEntity
import com.example.magatzem.data.NOMBRE_TRANSFERENCIA
import com.example.magatzem.data.esBanco
import com.example.magatzem.data.PagoFacturaEntity
import com.example.magatzem.data.PagosMitpv
import com.example.magatzem.ui.common.claveOrdenFecha
import com.example.magatzem.ui.common.fechaIso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Un albarán con el nombre del proveedor y sus importes (de la recepción si el albarán no los trae). */
data class AlbaranFila(val albaran: AlbaranEntity, val proveedor: String, val base: Double, val iva: Double)

/** Una factura con su proveedor, sus pagos/plazos y el nombre de la forma de pago de cada uno. */
data class FacturaFila(
    val factura: FacturaEntity,
    val proveedor: String,
    val pagos: List<PagoFacturaEntity>,
    val formaPorPago: Map<Long, String>
) {
    val total: Double get() = factura.base + factura.iva
}

/**
 * Oficina → Albaranes y Facturas.
 * - Un albarán se pasa a factura (número y fecha): nace pendiente de pago y el albarán se borra.
 * - Una factura pendiente se paga: contado o transferencia = un pago con su fecha (queda pagada);
 *   banco = 1, 2 o 3 plazos con importe y fecha cada uno, que se dan por pagados con su fecha.
 *   La factura pasa a pagada cuando todos sus plazos lo están.
 * - Los pagos por banco (y las transferencias, que salen del banco principal) **se descuentan del
 *   banco** cuando el pago queda pagado, con su fecha; si se devuelve a pendiente, el apunte se
 *   borra. El pago al contado todavía no toca la caja (pendiente de decidir cómo cuadra con MiTPV).
 */
class OficinaDocumentosViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val recepcionDao = db.recepcionDao()
    private val pagoDao = db.pagoFacturaDao()
    private val movimientoDao = db.movimientoDao()

    /** Mensaje para el usuario cuando una operación no se puede hacer (p. ej. falta el banco principal). */
    val aviso = MutableStateFlow<String?>(null)
    fun cerrarAviso() { aviso.value = null }

    private val proveedores = db.proveedorDao().observeAll()
    private val recepciones = recepcionDao.observeRecepciones()

    val albaranes: StateFlow<List<AlbaranFila>> =
        combine(recepcionDao.observeAlbaranes(), recepciones, proveedores) { albaranes, recs, provs ->
            val recPorId = recs.associateBy { it.id }
            val nombre = provs.associate { it.id to it.nombre }
            val exentos = provs.filter { it.exentoIva }.map { it.id }.toSet()
            albaranes.map { a ->
                val rec = recPorId[a.recepcionId]
                // Si el albarán se guardó sin importes (vacíos o a 0), se toman del total de la entrada.
                val sinImportes = (a.base ?: 0.0) == 0.0 && (a.iva ?: 0.0) == 0.0
                val base = if (sinImportes) rec?.let { it.totalConIva / factorCoste(rec.proveedorId in exentos) } ?: 0.0 else a.base ?: 0.0
                val iva = if (sinImportes) rec?.let { it.totalConIva - base } ?: 0.0 else a.iva ?: 0.0
                AlbaranFila(a, nombre[rec?.proveedorId] ?: "Sin proveedor", base, iva)
            }.sortedWith(compareBy({ it.proveedor.lowercase() }, { claveOrdenFecha(it.albaran.fecha) }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val facturas: StateFlow<List<FacturaFila>> =
        combine(recepcionDao.observeFacturas(), recepciones, proveedores, pagoDao.observeAll(), db.formaPagoDao().observeAll()) {
                facturas, recs, provs, pagos, formas ->
            val recPorId = recs.associateBy { it.id }
            val nombre = provs.associate { it.id to it.nombre }
            val pagosPorFactura = pagos.groupBy { it.facturaId }
            val formaPorId = formas.associate { it.id to it.nombre }
            facturas.map { f ->
                FacturaFila(
                    f,
                    nombre[recPorId[f.recepcionId]?.proveedorId] ?: "Sin proveedor",
                    pagosPorFactura[f.id].orEmpty(),
                    formaPorId
                )
            }.sortedWith(compareBy({ it.proveedor.lowercase() }, { claveOrdenFecha(it.factura.fecha) }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val formasPagoActivas: StateFlow<List<FormaPagoEntity>> = db.formaPagoDao().observeActivas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ya hay una factura de este proveedor con ese número: se pregunta si fusionar el albarán con ella. */
    data class DuplicadoFactura(val fila: AlbaranFila, val numero: String, val fecha: String, val existente: FacturaEntity, val tienePagos: Boolean)

    var duplicadoFactura by mutableStateOf<DuplicadoFactura?>(null)
        private set

    /** Respuesta al aviso: true = fusionar, false = crear aparte, null = cancelar. */
    fun resolverDuplicadoFactura(fusionar: Boolean?) {
        val d = duplicadoFactura ?: return
        duplicadoFactura = null
        if (fusionar != null) pasarAFactura(d.fila, d.numero, d.fecha, fusionar)
    }

    /**
     * Crea la factura (pendiente de pago) con los importes del albarán y borra el albarán, todo o nada. Si el
     * proveedor ya tiene una factura con ese número, [fusionar] null pregunta primero; fusionada, el albarán suma sus
     * líneas e importes a esa factura (una factura con pagos registrados no se fusiona).
     */
    fun pasarAFactura(fila: AlbaranFila, numero: String, fecha: String, fusionar: Boolean? = null) {
        viewModelScope.launch {
            val proveedorId = fila.albaran.recepcionId?.let { recepcionDao.obtenerRecepcion(it)?.proveedorId }
            var existente: FacturaEntity? = null
            if (proveedorId != null && numero.isNotBlank() && fusionar != false) {
                existente = recepcionDao.buscarFactura(proveedorId, numero.trim())
            }
            val tienePagos = existente?.let { db.pagoFacturaDao().obtenerPorFactura(it.id).isNotEmpty() } ?: false
            if (existente != null && fusionar == null) {
                duplicadoFactura = DuplicadoFactura(fila, numero, fecha, existente, tienePagos)
                return@launch
            }
            val destino = if (fusionar == true && !tienePagos) existente else null
            db.withTransaction {
                if (destino != null) {
                    val recAlbaran = fila.albaran.recepcionId
                    val recDestino = destino.recepcionId
                    if (recAlbaran != null && recDestino != null && recAlbaran != recDestino) {
                        val rAlbaran = recepcionDao.obtenerRecepcion(recAlbaran)
                        val rDestino = recepcionDao.obtenerRecepcion(recDestino)
                        recepcionDao.moverLineas(recAlbaran, recDestino)
                        if (rAlbaran != null && rDestino != null) {
                            recepcionDao.actualizarRecepcion(rDestino.copy(totalConIva = rDestino.totalConIva + rAlbaran.totalConIva))
                        }
                        recepcionDao.borrarAlbaran(fila.albaran.id)
                        recepcionDao.borrarRecepcion(recAlbaran)
                    } else {
                        recepcionDao.borrarAlbaran(fila.albaran.id)
                    }
                    val fusionada = destino.copy(base = destino.base + fila.base, iva = destino.iva + fila.iva)
                    recepcionDao.actualizarFactura(fusionada)
                    PagosMitpv.aplicarAFactura(db, fusionada)
                } else {
                    val factura = FacturaEntity(
                        recepcionId = fila.albaran.recepcionId,
                        numero = numero.trim(),
                        fecha = fechaIso(fecha),
                        base = fila.base,
                        iva = fila.iva,
                        estado = ESTADO_FACTURA_PENDIENTE
                    )
                    val facturaId = recepcionDao.insertarFactura(factura)
                    recepcionDao.borrarAlbaran(fila.albaran.id)
                    // Si MiTPV ya pagó esta factura en efectivo, queda pagada con la fecha de ese pago.
                    PagosMitpv.aplicarAFactura(db, factura.copy(id = facturaId))
                }
            }
        }
    }

    /** Banco principal (el marcado, o el único activo); null si no se puede decidir. */
    private suspend fun bancoPrincipal(): FormaPagoEntity? {
        val bancos = db.formaPagoDao().obtenerTodas().filter { it.esBanco() && it.activo }
        return bancos.firstOrNull { it.principal } ?: bancos.singleOrNull()
    }

    /** De qué banco sale un pago: el elegido, o el principal si es transferencia; null = no sale de un banco. */
    private suspend fun bancoDeSalida(formaPagoId: Long?): FormaPagoEntity? {
        val forma = db.formaPagoDao().obtenerTodas().firstOrNull { it.id == formaPagoId } ?: return null
        return when {
            forma.esBanco() -> forma
            forma.nombre == NOMBRE_TRANSFERENCIA -> bancoPrincipal()
            else -> null
        }
    }

    private suspend fun esTransferenciaSinBanco(formaPagoId: Long?): Boolean {
        val forma = db.formaPagoDao().obtenerTodas().firstOrNull { it.id == formaPagoId } ?: return false
        return forma.nombre == NOMBRE_TRANSFERENCIA && bancoPrincipal() == null
    }

    private val SIN_BANCO = "Una transferencia sale del banco principal y no hay ninguno. Márcalo en Ajustes → Bancos (o añade el banco en Formas de pago)."

    /** yyyy-MM-dd HH:mm:ss a partir de una fecha dd-MM-yyyy (los movimientos se guardan como el resto de marcas de tiempo). */
    private fun fechaMovimiento(fecha: String) = claveOrdenFecha(fecha.trim()) + " 00:00:00"

    private suspend fun conceptoPago(pago: PagoFacturaEntity): String {
        val factura = recepcionDao.obtenerFactura(pago.facturaId)
        val proveedorId = factura?.recepcionId?.let { recepcionDao.obtenerRecepcion(it)?.proveedorId }
        val proveedor = proveedorId?.let { id -> db.proveedorDao().obtenerTodos().firstOrNull { it.id == id }?.nombre }
        return "Pago factura Nº ${factura?.numero ?: "—"}" + (proveedor?.let { " ($it)" } ?: "") +
            if (pago.totalPlazos > 1) " · plazo ${pago.numeroPlazo}/${pago.totalPlazos}" else ""
    }

    /** Apunta (o actualiza) la salida del banco de un pago ya pagado. Sin banco de salida (contado) no hace nada. */
    private suspend fun apuntarEnBanco(pago: PagoFacturaEntity, fecha: String) {
        val banco = bancoDeSalida(pago.formaPagoId) ?: return
        val existente = movimientoDao.bancoDePago(pago.id)
        if (existente == null) {
            movimientoDao.insertarBanco(
                MovimientoBancoEntity(
                    bancoId = banco.id, fecha = fechaMovimiento(fecha), concepto = conceptoPago(pago),
                    importe = -pago.importe, pagoId = pago.id
                )
            )
        } else {
            movimientoDao.actualizarBanco(existente.copy(bancoId = banco.id, fecha = fechaMovimiento(fecha), importe = -pago.importe))
        }
    }

    /** Contado o transferencia: un único pago con su fecha; la factura queda pagada. */
    fun pagarDeUnaVez(factura: FacturaEntity, formaPagoId: Long, fecha: String) {
        viewModelScope.launch {
            if (esTransferenciaSinBanco(formaPagoId)) { aviso.value = SIN_BANCO; return@launch }
            db.withTransaction {
                pagoDao.eliminarPorFactura(factura.id)
                val pago = PagoFacturaEntity(
                    facturaId = factura.id, formaPagoId = formaPagoId, numeroPlazo = 1, totalPlazos = 1,
                    importe = factura.base + factura.iva, fechaPrevista = fechaIso(fecha), fechaPago = fechaIso(fecha)
                )
                val id = pagoDao.insertarUno(pago)
                apuntarEnBanco(pago.copy(id = id), fecha)
                recalcularEstado(factura.id)
            }
        }
    }

    /** Banco: deja planificados los plazos (importe, fecha prevista); siguen pendientes hasta darlos por pagados. */
    fun planificarBanco(factura: FacturaEntity, formaPagoId: Long, plazos: List<Pair<Double, String>>) {
        viewModelScope.launch {
            db.withTransaction {
                pagoDao.eliminarPorFactura(factura.id)
                pagoDao.insertar(
                    plazos.mapIndexed { i, (importe, fecha) ->
                        PagoFacturaEntity(
                            facturaId = factura.id, formaPagoId = formaPagoId, numeroPlazo = i + 1,
                            totalPlazos = plazos.size, importe = importe, fechaPrevista = fechaIso(fecha)
                        )
                    }
                )
                recalcularEstado(factura.id)
            }
        }
    }

    /** Da un plazo por pagado en [fecha] (o solo cambia la fecha si ya lo estaba); si sale de un banco, lo descuenta de él. */
    fun marcarPagado(pago: PagoFacturaEntity, fecha: String) {
        viewModelScope.launch {
            if (esTransferenciaSinBanco(pago.formaPagoId)) { aviso.value = SIN_BANCO; return@launch }
            db.withTransaction {
                val actualizado = pago.copy(fechaPago = fechaIso(fecha))
                pagoDao.actualizar(actualizado)
                apuntarEnBanco(actualizado, fecha)
                recalcularEstado(pago.facturaId)
            }
        }
    }

    /** Devuelve el pago a pendiente y deshace su salida del banco. */
    fun pasarAPendiente(pago: PagoFacturaEntity) {
        viewModelScope.launch {
            db.withTransaction {
                pagoDao.actualizar(pago.copy(fechaPago = null))
                movimientoDao.borrarBancoDePago(pago.id)
                recalcularEstado(pago.facturaId)
            }
        }
    }

    /** Descarta el plan de pago (por ejemplo, para rehacerlo con otros plazos). */
    fun anularPlan(factura: FacturaEntity) {
        viewModelScope.launch {
            db.withTransaction {
                pagoDao.eliminarPorFactura(factura.id)
                recalcularEstado(factura.id)
            }
        }
    }

    /** La factura está pagada cuando tiene pagos, todos con fecha, y suman su total. */
    private suspend fun recalcularEstado(facturaId: Long) = PagosMitpv.recalcularEstado(db, facturaId)
}
