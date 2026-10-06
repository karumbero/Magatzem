package com.example.magatzem.ui.oficina

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.AlbaranEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.data.RecepcionEntity
import com.example.magatzem.data.RecepcionLineaEntity
import com.example.magatzem.data.factorCoste
import com.example.magatzem.data.factorIva
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Una línea del albarán en edición: lo recibido originalmente y lo que se está tecleando ahora. */
class LineaAlbaran(
    val producto: ProductoEntity,
    /** Unidades que tenía el albarán (0 si la línea es nueva). */
    val cantidadOriginal: Int,
    val costeOriginal: Double,
    cantidad: String,
    coste: String
) {
    var cantidad by mutableStateOf(cantidad)
    var coste by mutableStateOf(coste)
}

/**
 * Oficina → Albaranes → abrir un albarán: sus líneas se pueden cambiar (cantidad y coste), quitar o añadir, y al guardar
 * los cambios se aplican a los artículos: la existencia sube o baja en la diferencia, y un coste distinto actualiza
 * coste y PVP igual que una entrada de mercancía. La base y el IVA del albarán se recalculan de las líneas.
 */
class AlbaranEditarViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val recepcionDao = db.recepcionDao()
    private val productoDao = db.productoDao()

    var albaran by mutableStateOf<AlbaranEntity?>(null)
        private set
    var proveedor by mutableStateOf<ProveedorEntity?>(null)
        private set
    val lineas = mutableStateListOf<LineaAlbaran>()
    private var recepcion: RecepcionEntity? = null

    private val _articulosProveedor = MutableStateFlow<List<ProductoEntity>>(emptyList())
    /** Artículos del proveedor que todavía no están en el albarán (para añadirlos). */
    val articulosProveedor: StateFlow<List<ProductoEntity>> = _articulosProveedor.asStateFlow()

    var error by mutableStateOf<String?>(null)
        private set
    var cargado by mutableStateOf(false)
        private set

    private var cargadoId: Long? = null

    fun cargar(albaranId: Long) {
        if (cargadoId == albaranId) return
        cargadoId = albaranId
        viewModelScope.launch {
            val alb = recepcionDao.obtenerAlbaran(albaranId) ?: return@launch
            val rec = alb.recepcionId?.let { recepcionDao.obtenerRecepcion(it) }
            albaran = alb
            recepcion = rec
            proveedor = rec?.proveedorId?.let { id -> db.proveedorDao().obtenerTodos().firstOrNull { it.id == id } }
            lineas.clear()
            // Una misma línea por artículo (si un albarán fusionado repite artículo, se suman sus cantidades).
            val porProducto = linkedMapOf<Long, RecepcionLineaEntity>()
            rec?.let { recepcionDao.obtenerLineas(it.id) }.orEmpty().forEach { l ->
                val previa = porProducto[l.productoId]
                porProducto[l.productoId] = if (previa == null) l else l.copy(cantidad = previa.cantidad + l.cantidad)
            }
            porProducto.values.forEach { l ->
                val p = productoDao.obtenerPorId(l.productoId) ?: return@forEach
                lineas += LineaAlbaran(p, l.cantidad, l.coste, l.cantidad.toString(), formato(l.coste))
            }
            refrescarArticulos()
            cargado = true
        }
    }

    private suspend fun refrescarArticulos() {
        val provId = recepcion?.proveedorId ?: return
        val enAlbaran = lineas.map { it.producto.id }.toSet()
        _articulosProveedor.value = productoDao.obtenerTodosOrdenados()
            .filter { it.proveedorId == provId && it.id !in enAlbaran }
            .sortedBy { (it.sku ?: it.nombre).lowercase() }
    }

    fun anadir(producto: ProductoEntity) {
        if (lineas.any { it.producto.id == producto.id }) return
        lineas += LineaAlbaran(producto, 0, producto.coste, "1", formato(producto.coste))
        viewModelScope.launch { refrescarArticulos() }
    }

    fun quitar(linea: LineaAlbaran) {
        lineas.remove(linea)
        viewModelScope.launch { refrescarArticulos() }
    }

    private fun formato(v: Double) = if (v == 0.0) "0" else v.toString().removeSuffix(".0")

    private fun cantidadDe(l: LineaAlbaran): Int? = l.cantidad.trim().toIntOrNull()?.takeIf { it > 0 }
    private fun costeDe(l: LineaAlbaran): Double? = l.coste.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

    /** Base (suma cantidad × coste) de las líneas tal como están ahora, sin impuestos. */
    fun baseActual(): Double = lineas.sumOf { (cantidadDe(it) ?: 0) * (costeDe(it) ?: 0.0) }

    val exento: Boolean get() = proveedor?.exentoIva == true

    fun guardar(onExito: () -> Unit) {
        val alb = albaran ?: return
        val rec = recepcion ?: run { error = "Este albarán no tiene entrada asociada"; return }
        if (lineas.isEmpty()) {
            error = "El albarán debe tener al menos una línea"
            return
        }
        for (l in lineas) {
            if (cantidadDe(l) == null || costeDe(l) == null) {
                error = "Revisa la cantidad y el coste de \"${l.producto.nombre}\""
                return
            }
        }
        viewModelScope.launch {
            val ahora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val factorCoste = factorCoste(exento)
            var mensaje: String? = null
            db.withTransaction {
                // Comprobación previa: ningún artículo puede quedarse con existencia negativa.
                val originales = recepcionDao.obtenerLineas(rec.id).groupBy { it.productoId }.mapValues { (_, ls) -> ls.sumOf { it.cantidad } }
                val enEdicion = lineas.associateBy { it.producto.id }
                for ((productoId, antes) in originales) {
                    val p = productoDao.obtenerPorId(productoId) ?: continue
                    val despues = enEdicion[productoId]?.let { cantidadDe(it) } ?: 0
                    if (p.existencia + (despues - antes) < 0) {
                        mensaje = "\"${p.nombre}\" quedaría con existencia negativa (${p.existencia + (despues - antes)}). Revisa la cantidad."
                        return@withTransaction
                    }
                }
                // Artículos que se quitan del albarán: se resta lo que había entrado.
                for ((productoId, antes) in originales) {
                    if (productoId !in enEdicion) productoDao.sumarExistencia(productoId, -antes)
                }
                val nuevas = mutableListOf<RecepcionLineaEntity>()
                for (l in lineas) {
                    val p = productoDao.obtenerPorId(l.producto.id) ?: continue
                    val cantidad = cantidadDe(l)!!
                    val coste = costeDe(l)!!
                    val delta = cantidad - (originales[p.id] ?: 0)
                    // Mismas reglas de margen/PVP que una entrada de mercancía.
                    val (margen, pvp) = when {
                        coste <= 0 -> p.margenBeneficio to p.precioVenta
                        p.margenBeneficio == 0.0 && p.precioVenta > 0 -> (((p.precioVenta / (coste * factorCoste)) - 1) * 100) to p.precioVenta
                        else -> p.margenBeneficio to ProductoEntity.calcularPrecioVenta(coste, p.margenBeneficio, exento)
                    }
                    val cambiaCoste = coste != l.costeOriginal
                    if (delta != 0 || cambiaCoste) {
                        productoDao.registrarEntrada(p.id, delta, coste, margen, pvp, ahora)
                    }
                    nuevas += RecepcionLineaEntity(
                        recepcionId = rec.id, productoId = p.id, cantidad = cantidad, coste = coste,
                        margenBeneficio = margen, subtotalConIva = cantidad * coste * factorCoste
                    )
                }
                recepcionDao.eliminarLineas(rec.id)
                recepcionDao.insertarLineas(nuevas)
                recepcionDao.actualizarRecepcion(rec.copy(totalConIva = nuevas.sumOf { it.subtotalConIva }))
                val base = nuevas.sumOf { it.cantidad * it.coste }
                recepcionDao.actualizarAlbaran(alb.copy(base = base, iva = base * (factorIva(exento) - 1)))
            }
            if (mensaje != null) {
                error = mensaje
            } else {
                error = null
                onExito()
            }
        }
    }
}
