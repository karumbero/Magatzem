package com.example.magatzem.ui.documentos

import androidx.room.withTransaction
import com.example.magatzem.data.AlbaranEntity
import com.example.magatzem.data.AppDatabase
import com.example.magatzem.data.FacturaEntity
import com.example.magatzem.data.PagosMitpv
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.RecepcionEntity
import com.example.magatzem.data.RecepcionLineaEntity
import com.example.magatzem.data.factorCoste
import com.example.magatzem.data.factorIva
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Albarán o factura de Movimientos. */
enum class TipoDoc(val nombre: String) { ALBARAN("Albarán"), FACTURA("Factura") }

/** Un documento abierto: su entrada (recepción) y sus datos. */
class DocAbierto(val tipo: TipoDoc, val docId: Long, val recepcionId: Long, val proveedorId: Long?, val numero: String, val fecha: String)

/**
 * Un artículo con existencia anterior cuyo PVP ha cambiado al entrar mercancía con otro coste: las unidades que ya había en la
 * tienda llevan la etiqueta del precio antiguo y hay que remarcarlas.
 */
data class AvisoRemarcar(val productoId: Long, val nombre: String, val sku: String?, val unidadesAnteriores: Int, val pvpAntes: Double, val pvpNuevo: Double)

/** Resultado de guardar una línea: un error (y nada cambia), o bien guardada, con un aviso de remarcar si procede. */
class ResultadoLinea(val error: String? = null, val remarcar: AvisoRemarcar? = null)

/** Una línea tal como está guardada (una por artículo). */
class LineaGuardada(val producto: ProductoEntity, val cantidad: Int, val coste: Double)

/**
 * Lógica de guardado de albaranes y facturas, sin estado: cada operación lee de la base de datos lo que hay ahora (no de la
 * pantalla), calcula la DIFERENCIA y la aplica a los artículos, y reescribe solo la línea afectada. Por eso no se duplican
 * líneas, las cantidades editadas se sustituyen (no se suman) y lo que no se toca queda exactamente como estaba.
 */
class DocumentoMotor(private val db: AppDatabase) {
    private val recepcionDao = db.recepcionDao()
    private val productoDao = db.productoDao()
    private val consumoDao = db.consumoCapaDao()

    private fun ahora() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private suspend fun exento(proveedorId: Long?): Boolean =
        proveedorId != null && db.proveedorDao().obtenerTodos().firstOrNull { it.id == proveedorId }?.exentoIva == true

    /** Crea un documento vacío (entrada + albarán/factura) y devuelve su id. */
    suspend fun crear(tipo: TipoDoc, proveedorId: Long, numero: String, fechaIso: String): Long = db.withTransaction {
        val rec = recepcionDao.insertarRecepcion(RecepcionEntity(proveedorId = proveedorId, fecha = ahora(), totalConIva = 0.0))
        when (tipo) {
            TipoDoc.ALBARAN -> recepcionDao.insertarAlbaran(
                AlbaranEntity(recepcionId = rec, numero = numero.ifBlank { null }, fecha = fechaIso, base = 0.0, iva = 0.0)
            )
            TipoDoc.FACTURA -> recepcionDao.insertarFactura(
                FacturaEntity(recepcionId = rec, numero = numero.ifBlank { null }, fecha = fechaIso, base = 0.0, iva = 0.0)
            )
        }
    }

    suspend fun buscarPorNumero(tipo: TipoDoc, proveedorId: Long, numero: String): Long? = when (tipo) {
        TipoDoc.ALBARAN -> recepcionDao.buscarAlbaran(proveedorId, numero)?.id
        TipoDoc.FACTURA -> recepcionDao.buscarFactura(proveedorId, numero)?.id
    }

    suspend fun abrir(tipo: TipoDoc, docId: Long): DocAbierto? = when (tipo) {
        TipoDoc.ALBARAN -> recepcionDao.obtenerAlbaran(docId)?.let { a ->
            a.recepcionId?.let { r -> DocAbierto(tipo, a.id, r, recepcionDao.obtenerRecepcion(r)?.proveedorId, a.numero.orEmpty(), a.fecha) }
        }
        TipoDoc.FACTURA -> recepcionDao.obtenerFactura(docId)?.let { f ->
            f.recepcionId?.let { r -> DocAbierto(tipo, f.id, r, recepcionDao.obtenerRecepcion(r)?.proveedorId, f.numero.orEmpty(), f.fecha) }
        }
    }

    /** Las líneas guardadas, una por artículo (si hubiera filas repetidas de un artículo, se suman). */
    suspend fun lineas(recepcionId: Long): List<LineaGuardada> {
        val porProducto = linkedMapOf<Long, Pair<Int, Double>>()
        recepcionDao.obtenerLineas(recepcionId).forEach { l ->
            val previa = porProducto[l.productoId]
            porProducto[l.productoId] = if (previa == null) l.cantidad to l.coste else (previa.first + l.cantidad) to previa.second
        }
        return porProducto.mapNotNull { (id, v) -> productoDao.obtenerPorId(id)?.let { LineaGuardada(it, v.first, v.second) } }
    }

    /**
     * Deja la línea del artículo con esa cantidad y coste. Aplica a la existencia SOLO la diferencia con lo que había
     * guardado; el coste/margen/PVP del artículo solo se tocan si el coste de la línea cambia (o es una línea nueva).
     * Devuelve un mensaje de error (y no cambia nada) si la existencia quedaría negativa.
     */
    suspend fun guardarLinea(recepcionId: Long, productoId: Long, cantidad: Int, coste: Double): ResultadoLinea {
        var error: String? = null
        var remarcar: AvisoRemarcar? = null
        db.withTransaction {
            val rec = recepcionDao.obtenerRecepcion(recepcionId) ?: run { error = "No se encuentra el documento"; return@withTransaction }
            val exento = exento(rec.proveedorId)
            val p = productoDao.obtenerPorId(productoId) ?: return@withTransaction
            val previas = recepcionDao.obtenerLineas(recepcionId).filter { it.productoId == productoId }
            val antes = previas.sumOf { it.cantidad }
            val costeAntes = previas.firstOrNull()?.coste
            val delta = cantidad - antes
            // Capa de coste: no puede quedar con menos unidades de las que ya se han vendido o retirado.
            val gastado = consumoDao.consumido(recepcionId, productoId)
            if (cantidad < gastado) {
                error = "De \"${p.nombre}\" ya se han vendido o retirado $gastado uds de esta entrada: no puede quedar con menos."
                return@withTransaction
            }
            if (p.existencia + delta < 0) {
                error = "\"${p.nombre}\" quedaría con existencia negativa (${p.existencia + delta})."
                return@withTransaction
            }
            var margenFinal = previas.firstOrNull()?.margenBeneficio ?: p.margenBeneficio
            if (costeAntes == null || coste != costeAntes) {
                // Mismas reglas de margen/PVP que una entrada de mercancía.
                val (margen, pvp) = when {
                    coste <= 0 -> p.margenBeneficio to p.precioVenta
                    p.margenBeneficio == 0.0 && p.precioVenta > 0 -> (((p.precioVenta / (coste * factorCoste(exento))) - 1) * 100) to p.precioVenta
                    else -> p.margenBeneficio to ProductoEntity.calcularPrecioVenta(coste, p.margenBeneficio, exento)
                }
                margenFinal = margen
                productoDao.registrarEntrada(productoId, delta, coste, margen, pvp, ahora())
                // Con existencia anterior (sin contar lo que entra por esta línea) y un PVP distinto, hay que remarcar.
                val unidadesAnteriores = p.existencia - antes
                if (unidadesAnteriores > 0 && Math.abs(pvp - p.precioVenta) >= 0.005) {
                    remarcar = AvisoRemarcar(productoId, p.nombre, p.sku, unidadesAnteriores, p.precioVenta, pvp)
                }
            } else if (delta != 0) {
                productoDao.sumarExistencia(productoId, delta)
            }
            recepcionDao.eliminarLineasDeProducto(recepcionId, productoId)
            recepcionDao.insertarLineas(
                listOf(
                    RecepcionLineaEntity(
                        recepcionId = recepcionId, productoId = productoId, cantidad = cantidad, coste = coste,
                        margenBeneficio = margenFinal, subtotalConIva = cantidad * coste * factorCoste(exento)
                    )
                )
            )
            recalcular(recepcionId)
        }
        return ResultadoLinea(error, remarcar)
    }

    /** Quita la línea y resta de la existencia lo que había entrado. */
    suspend fun quitarLinea(recepcionId: Long, productoId: Long): String? {
        var error: String? = null
        db.withTransaction {
            val p = productoDao.obtenerPorId(productoId)
            val antes = recepcionDao.obtenerLineas(recepcionId).filter { it.productoId == productoId }.sumOf { it.cantidad }
            val gastado = consumoDao.consumido(recepcionId, productoId)
            if (p != null && gastado > 0) {
                error = "De \"${p.nombre}\" ya se han vendido o retirado $gastado uds de esta entrada: no se puede quitar la línea."
                return@withTransaction
            }
            if (p != null && p.existencia - antes < 0) {
                error = "\"${p.nombre}\" quedaría con existencia negativa (${p.existencia - antes})."
                return@withTransaction
            }
            if (p != null && antes != 0) productoDao.sumarExistencia(productoId, -antes)
            recepcionDao.eliminarLineasDeProducto(recepcionId, productoId)
            recalcular(recepcionId)
        }
        return error
    }

    /** Total de la entrada y base/IVA del albarán o factura, siempre a partir de las líneas guardadas. */
    private suspend fun recalcular(recepcionId: Long) {
        val rec = recepcionDao.obtenerRecepcion(recepcionId) ?: return
        val exento = exento(rec.proveedorId)
        val lineas = recepcionDao.obtenerLineas(recepcionId)
        val base = lineas.sumOf { it.cantidad * it.coste }
        recepcionDao.actualizarRecepcion(rec.copy(totalConIva = lineas.sumOf { it.subtotalConIva }))
        recepcionDao.buscarAlbaranPorRecepcion(recepcionId)?.let {
            recepcionDao.actualizarAlbaran(it.copy(base = base, iva = base * (factorIva(exento) - 1)))
        }
        recepcionDao.buscarFacturaPorRecepcion(recepcionId)?.let {
            val f = it.copy(base = Math.round(base * 100) / 100.0, iva = Math.round(base * (factorCoste(exento) - 1) * 100) / 100.0)
            recepcionDao.actualizarFactura(f)
            PagosMitpv.aplicarAFactura(db, f)
        }
    }

    /** Borra un documento que se quedó sin líneas (la entrada y el albarán/factura vacíos). */
    suspend fun borrarSiVacio(tipo: TipoDoc, docId: Long) {
        db.withTransaction {
            val doc = abrir(tipo, docId) ?: return@withTransaction
            if (recepcionDao.obtenerLineas(doc.recepcionId).isNotEmpty()) return@withTransaction
            when (tipo) {
                TipoDoc.ALBARAN -> recepcionDao.borrarAlbaran(docId)
                TipoDoc.FACTURA -> recepcionDao.borrarFactura(docId)
            }
            recepcionDao.borrarRecepcion(doc.recepcionId)
        }
    }

    /** ¿Tiene pagos o un plan de pago registrados? (entonces no se puede cambiar sus importes). */
    suspend fun facturaConPagos(facturaId: Long): Boolean = db.pagoFacturaDao().obtenerPorFactura(facturaId).isNotEmpty()

    /**
     * Albarán → factura: la factura queda con las mismas líneas (sin tocar existencias) y el albarán desaparece. Si el proveedor
     * ya tiene una factura con ese número y [fusionar] es true, las líneas pasan a ella. Devuelve el id de la factura.
     */
    suspend fun albaranAFactura(albaranId: Long, numero: String, fechaIso: String, fusionar: Boolean): Long? = db.withTransaction {
        val a = abrir(TipoDoc.ALBARAN, albaranId) ?: return@withTransaction null
        val proveedorId = a.proveedorId
        val existente = proveedorId?.let { recepcionDao.buscarFactura(it, numero) }
        if (existente != null && fusionar) {
            val destino = existente.recepcionId
            if (destino != null && destino != a.recepcionId) {
                recepcionDao.moverLineas(a.recepcionId, destino)
                recepcionDao.borrarAlbaran(albaranId)
                recepcionDao.borrarRecepcion(a.recepcionId)
                recalcular(destino)
            }
            return@withTransaction existente.id
        }
        val rec = recepcionDao.obtenerRecepcion(a.recepcionId)
        val exento = exento(proveedorId)
        val base = recepcionDao.obtenerLineas(a.recepcionId).sumOf { it.cantidad * it.coste }
        val f = FacturaEntity(
            recepcionId = a.recepcionId, numero = numero.ifBlank { null }, fecha = fechaIso,
            base = Math.round(base * 100) / 100.0, iva = Math.round(base * (factorCoste(exento) - 1) * 100) / 100.0
        )
        val id = recepcionDao.insertarFactura(f)
        recepcionDao.borrarAlbaran(albaranId)
        PagosMitpv.aplicarAFactura(db, f.copy(id = id))
        rec?.let { id }
    }
}
