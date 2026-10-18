package com.example.magatzem.ui.sol

import android.app.Application
import com.example.magatzem.ui.common.OrdenNatural
import com.example.magatzem.ui.common.ordenNatural
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.RetiradaEntity
import com.example.magatzem.data.RetiradaLineaEntity
import com.example.magatzem.ui.common.contieneBusqueda
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una línea de la retirada en curso (todavía sin guardar). */
data class LineaRetirar(val producto: ProductoEntity, val cantidad: Int, val pvp: Double) {
    val importe: Double get() = cantidad * pvp
}

class RetirarViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val dao = db.retiradaDao()

    val productos: StateFlow<List<ProductoEntity>> = db.productoDao().observeAll().ordenNatural()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val retiradas = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lineas = mutableStateListOf<LineaRetirar>()
    var mensaje by mutableStateOf<String?>(null)
        private set

    val total: Double get() = lineas.sumOf { it.importe }

    /** Coincidencias para la búsqueda (SKU, REF, EAN o nombre, sin acentos). */
    fun buscar(texto: String, todos: List<ProductoEntity>): List<ProductoEntity> =
        if (texto.isBlank()) emptyList()
        else todos.filter { contieneBusqueda(texto, it.sku, it.nombre, it.codigoBarras, it.referenciaFabricante) }
            .sortedWith(compareBy(OrdenNatural) { it.nombre }).take(8)

    /** El artículo cuyo SKU, REF o EAN es exactamente lo tecleado/escaneado (vale también el único que coincide). */
    fun exacto(texto: String, todos: List<ProductoEntity>): ProductoEntity? {
        val t = texto.trim()
        if (t.isEmpty()) return null
        todos.firstOrNull { it.sku.equals(t, true) || it.codigoBarras.equals(t, true) || it.referenciaFabricante.equals(t, true) }?.let { return it }
        return buscar(t, todos).singleOrNull()
    }

    /** El PVP es el que se teclee (por defecto el del artículo): una retirada no es una venta, el importe solo es una valoración. */
    fun anadir(producto: ProductoEntity, cantidad: Int, pvp: Double) {
        if (cantidad <= 0) return
        mensaje = null
        val i = lineas.indexOfFirst { it.producto.id == producto.id && it.pvp == pvp }
        if (i >= 0) lineas[i] = lineas[i].copy(cantidad = lineas[i].cantidad + cantidad)
        else lineas.add(LineaRetirar(producto, cantidad, pvp))
    }

    fun cambiarPvp(indice: Int, pvp: Double) {
        if (indice in lineas.indices) lineas[indice] = lineas[indice].copy(pvp = pvp)
    }

    fun cambiarCantidad(indice: Int, cantidad: Int) {
        if (indice in lineas.indices && cantidad > 0) lineas[indice] = lineas[indice].copy(cantidad = cantidad)
    }

    fun quitar(indice: Int) {
        if (indice in lineas.indices) lineas.removeAt(indice)
    }

    /** Guarda las líneas con la fecha de ahora y deja la pantalla lista para otra retirada. */
    fun finalizar() {
        if (lineas.isEmpty()) return
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val copia = lineas.toList()
        viewModelScope.launch {
            db.withTransaction {
                dao.guardar(
                    RetiradaEntity(fecha = fecha, total = copia.sumOf { it.importe }),
                    copia.map {
                        RetiradaLineaEntity(
                            retiradaId = 0, productoId = it.producto.id, sku = it.producto.sku, nombre = it.producto.nombre,
                            cantidad = it.cantidad, pvp = it.pvp, importe = it.importe
                        )
                    }
                )
                // Se descuenta del stock actual (sin capas de coste ni beneficio: no es una venta).
                copia.groupBy { it.producto.id }.forEach { (id, ls) -> db.productoDao().sumarExistencia(id, -ls.sumOf { it.cantidad }) }
            }
            lineas.clear()
            mensaje = "Retirada guardada (${copia.size} línea${if (copia.size == 1) "" else "s"}) y descontada del stock."
        }
    }

    suspend fun lineasDe(retiradaId: Long) = dao.lineasDe(retiradaId)
}
