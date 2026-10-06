package com.example.magatzem.ui.productos

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.ProductoDao
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun nowTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

class ProductoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val productoDao: ProductoDao = db.productoDao()

    /** Para "Existencia": todos los artículos, sin distinguir Pre/Nuevo (distinción descartada el 2026-09-30). */
    val productos: StateFlow<List<ProductoEntity>> = productoDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorias: StateFlow<List<CategoriaEntity>> = db.categoriaDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proveedores: StateFlow<List<ProveedorEntity>> = db.proveedorDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set

    var productoExistente by mutableStateOf<ProductoEntity?>(null)
        private set

    fun limpiarError() {
        error = null
    }

    fun limpiarProductoExistente() {
        productoExistente = null
    }

    /**
     * Comprobación al terminar de escanear/teclear el código de barras, el SKU o la REF (antes de
     * rellenar el resto del formulario), no solo al pulsar Guardar: si ya existe un artículo con
     * ese dato, avisa enseguida en vez de dejar descubrirlo al final. `excludeId` sirve para poder
     * reutilizar esto también al editar, sin que el propio artículo se detecte como "duplicado".
     */
    fun comprobarDuplicadoTemprano(
        sku: String,
        codigoBarras: String,
        referenciaFabricante: String,
        excludeId: Long = -1
    ) {
        val skuLimpio = sku.trim().ifBlank { null }
        val codigoLimpio = codigoBarras.trim()
        val referenciaLimpia = referenciaFabricante.trim()
        if (skuLimpio == null && codigoLimpio.isEmpty() && referenciaLimpia.isEmpty()) return
        viewModelScope.launch {
            val duplicado = productoDao.buscarDuplicado(skuLimpio, codigoLimpio, referenciaLimpia, excludeId)
            if (duplicado != null) productoExistente = duplicado
        }
    }

    private data class Datos(
        val sku: String?,
        val codigoBarras: String?,
        val referenciaFabricante: String?,
        val nombre: String,
        val categoriaId: Long?,
        val proveedorId: Long?,
        val coste: Double,
        val margen: Double,
        val precioVenta: Double,
        val existencia: Int,
        val minimo: Int,
        val maximo: Int?
    )

    private fun validarYConstruir(
        sku: String,
        codigoBarras: String,
        referenciaFabricante: String,
        nombre: String,
        categoriaId: Long?,
        proveedorId: Long?,
        coste: String,
        margen: String,
        precioVenta: String,
        existencia: String,
        minimo: String,
        maximo: String
    ): Datos? {
        // Categoría y proveedor son obligatorios (desde 2026-10-01): así un artículo entrado por
        // Existencia siempre se puede localizar después desde Movimientos → Entradas, que solo
        // acepta artículos del proveedor elegido. SKU y nombre siguen sin ser obligatorios. Además,
        // Máximo, si se ha tecleado algo, tiene que ser un número de verdad.
        if (categoriaId == null || proveedorId == null) {
            error = "La categoría y el proveedor son obligatorios"
            return null
        }
        val maximoValor = if (maximo.isBlank()) null else maximo.toIntOrNull()
        if (maximo.isNotBlank() && maximoValor == null) {
            error = "Revisa el campo Máximo"
            return null
        }
        return Datos(
            sku = sku.trim().ifBlank { null },
            codigoBarras = codigoBarras.trim().ifBlank { null },
            referenciaFabricante = referenciaFabricante.trim().ifBlank { null },
            nombre = nombre.trim(),
            categoriaId = categoriaId,
            proveedorId = proveedorId,
            coste = coste.replace(',', '.').toDoubleOrNull() ?: 0.0,
            margen = margen.replace(',', '.').toDoubleOrNull() ?: 0.0,
            precioVenta = precioVenta.replace(',', '.').toDoubleOrNull() ?: 0.0,
            existencia = existencia.toIntOrNull() ?: 0,
            minimo = minimo.toIntOrNull() ?: 0,
            maximo = maximoValor
        )
    }

    /** No toca capas ni movimientos: en esta etapa `existencia` es un número simple. */
    fun crear(
        sku: String,
        codigoBarras: String,
        referenciaFabricante: String,
        nombre: String,
        categoriaId: Long?,
        proveedorId: Long?,
        coste: String,
        margen: String,
        precioVenta: String,
        existencia: String,
        minimo: String,
        maximo: String,
        mostrarEnTeclado: Boolean,
        onSuccess: () -> Unit
    ) {
        val datos = validarYConstruir(
            sku, codigoBarras, referenciaFabricante, nombre, categoriaId, proveedorId,
            coste, margen, precioVenta, existencia, minimo, maximo
        ) ?: return
        viewModelScope.launch {
            val duplicado = productoDao.buscarDuplicado(
                datos.sku, datos.codigoBarras.orEmpty(), datos.referenciaFabricante.orEmpty()
            )
            if (duplicado != null) {
                productoExistente = duplicado
                return@launch
            }
            try {
                val ahora = nowTimestamp()
                productoDao.insert(
                    ProductoEntity(
                        sku = datos.sku,
                        codigoBarras = datos.codigoBarras,
                        referenciaFabricante = datos.referenciaFabricante,
                        nombre = datos.nombre,
                        categoriaId = datos.categoriaId,
                        proveedorId = datos.proveedorId,
                        coste = datos.coste,
                        margenBeneficio = datos.margen,
                        // El PVP se teclea directamente (no siempre se deriva de coste+margen: con
                        // coste 0 el PVP real igual se conoce y hay que respetarlo tal cual).
                        precioVenta = datos.precioVenta,
                        existencia = datos.existencia,
                        minimo = datos.minimo,
                        maximo = datos.maximo,
                        mostrarEnTeclado = mostrarEnTeclado,
                        fechaCreacion = ahora,
                        fechaActualizacion = ahora
                    )
                )
                error = null
                onSuccess()
            } catch (e: SQLiteConstraintException) {
                error = "El SKU o el código de barras ya existen"
            }
        }
    }

    fun actualizar(
        producto: ProductoEntity,
        sku: String,
        codigoBarras: String,
        referenciaFabricante: String,
        nombre: String,
        categoriaId: Long?,
        proveedorId: Long?,
        coste: String,
        margen: String,
        precioVenta: String,
        existencia: String,
        minimo: String,
        maximo: String,
        mostrarEnTeclado: Boolean,
        onSuccess: () -> Unit
    ) {
        val datos = validarYConstruir(
            sku, codigoBarras, referenciaFabricante, nombre, categoriaId, proveedorId,
            coste, margen, precioVenta, existencia, minimo, maximo
        ) ?: return
        viewModelScope.launch {
            val duplicado = productoDao.buscarDuplicado(
                datos.sku, datos.codigoBarras.orEmpty(), datos.referenciaFabricante.orEmpty(), producto.id
            )
            if (duplicado != null) {
                productoExistente = duplicado
                return@launch
            }
            try {
                productoDao.update(
                    producto.copy(
                        sku = datos.sku,
                        codigoBarras = datos.codigoBarras,
                        referenciaFabricante = datos.referenciaFabricante,
                        nombre = datos.nombre,
                        categoriaId = datos.categoriaId,
                        proveedorId = datos.proveedorId,
                        coste = datos.coste,
                        margenBeneficio = datos.margen,
                        precioVenta = datos.precioVenta,
                        existencia = datos.existencia,
                        minimo = datos.minimo,
                        maximo = datos.maximo,
                        mostrarEnTeclado = mostrarEnTeclado,
                        fechaActualizacion = nowTimestamp()
                    )
                )
                error = null
                onSuccess()
            } catch (e: SQLiteConstraintException) {
                error = "El SKU o el código de barras ya existen"
            }
        }
    }

    /** Por qué no se puede borrar el último artículo que se intentó borrar (null = no hay aviso). */
    var avisoEliminar by mutableStateOf<String?>(null)
        private set

    fun cerrarAvisoEliminar() {
        avisoEliminar = null
    }

    /**
     * Solo se borra un artículo sin existencia y sin ningún movimiento: ni en entradas, albaranes o facturas, ni en pedidos, ni
     * con incidencias, ni con ventas importadas. Si no, no se borra y se avisa de por qué (hay que dejar la existencia a 0
     * con una incidencia o una entrada, y un artículo con historial no se borra).
     */
    fun eliminar(producto: ProductoEntity) {
        viewModelScope.launch {
            val actual = productoDao.obtenerPorId(producto.id) ?: return@launch
            val motivos = buildList {
                if (actual.existencia != 0) add("tiene existencia (${actual.existencia})")
                if (productoDao.contarLineasRecepcion(actual.id) > 0) add("aparece en entradas, albaranes o facturas")
                if (productoDao.contarLineasPedido(actual.id) > 0) add("aparece en pedidos")
                if (productoDao.contarIncidencias(actual.id) > 0) add("tiene incidencias")
                if (productoDao.contarVentasImportadas(actual.id) > 0) add("tiene ventas importadas")
            }
            if (motivos.isNotEmpty()) {
                avisoEliminar = "No se puede borrar \"${actual.nombre}\": " + motivos.joinToString(", ") + "."
                return@launch
            }
            productoDao.delete(actual)
        }
    }
}
