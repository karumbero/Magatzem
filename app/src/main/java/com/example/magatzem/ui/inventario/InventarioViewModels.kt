package com.example.magatzem.ui.inventario

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.INVENTARIO_ABIERTO
import com.example.magatzem.data.INVENTARIO_CERRADO
import com.example.magatzem.data.InventarioEntity
import com.example.magatzem.data.InventarioLineaEntity
import com.example.magatzem.data.InventarioResumen
import com.example.magatzem.data.ProductoEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun ahora(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

/** Listado de inventarios y creación de uno nuevo (con todos los artículos). */
class InventarioListaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val dao = db.inventarioDao()

    val inventarios: StateFlow<List<InventarioResumen>> = dao.observeResumenes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Crea el inventario con una fila por artículo: su existencia actual y las bajas (incidencias) acumuladas; sin contar todavía.
     * Devuelve el id por el callback.
     */
    fun nuevo(nombre: String, onCreado: (Long) -> Unit) {
        viewModelScope.launch {
            val id = db.withTransaction {
                val inventarioId = dao.insertar(InventarioEntity(nombre = nombre.trim().ifBlank { "Inventario" }, fecha = ahora()))
                val categorias = db.categoriaDao().obtenerTodas().associate { it.id to it.nombre }
                val proveedores = db.proveedorDao().obtenerTodos().associate { it.id to it.nombre }
                val bajas = dao.bajasPorProducto().associate { it.productoId to it.bajas }
                // Entran todos los artículos; los que no tienen existencia ni bajas quedan ocultos en pantalla (hay un botón para verlos).
                val productos: List<ProductoEntity> = db.productoDao().obtenerTodosOrdenados()
                dao.insertarLineas(
                    productos.map { p ->
                        InventarioLineaEntity(
                            inventarioId = inventarioId, productoId = p.id, sku = p.sku, referencia = p.referenciaFabricante,
                            codigoBarras = p.codigoBarras, nombre = p.nombre.ifBlank { "(sin nombre)" },
                            categoria = p.categoriaId?.let { categorias[it] }, proveedor = p.proveedorId?.let { proveedores[it] },
                            existenciaInicial = p.existencia, bajas = bajas[p.id] ?: 0
                        )
                    }
                )
                inventarioId
            }
            onCreado(id)
        }
    }

    fun borrar(inventario: InventarioEntity) {
        viewModelScope.launch { dao.borrar(inventario.id) }
    }
}

/** Un inventario abierto: sus filas, el recuento y los filtros. Cada cambio se guarda al momento. */
class InventarioViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val dao = db.inventarioDao()
    private val productoDao = db.productoDao()

    val proveedores = db.proveedorDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var inventarioId: Long = -1
    val productos: StateFlow<List<ProductoEntity>> = productoDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun inventario(id: Long): StateFlow<InventarioEntity?> {
        inventarioId = id
        return dao.observeInventario(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun lineas(id: Long): StateFlow<List<InventarioLineaEntity>> =
        dao.observeLineas(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var mensaje by mutableStateOf<String?>(null)
        private set

    fun limpiarMensaje() {
        mensaje = null
    }

    /** Guarda las unidades contadas de una fila (null = sin contar) junto con la existencia que tenía el artículo en ese momento. */
    fun contar(linea: InventarioLineaEntity, contadas: Int?) {
        viewModelScope.launch {
            val existencia = productoDao.obtenerPorId(linea.productoId)?.existencia ?: linea.existenciaInicial
            if (contadas == null) dao.contar(linea.id, null, null, null) else dao.contar(linea.id, contadas, existencia, ahora())
        }
    }

    /**
     * Lector o SKU/REF/código escrito + Intro: suma 1 unidad contada al artículo (contando desde cero la primera vez). Devuelve false
     * si no está en este inventario.
     */
    fun sumarUno(lineas: List<InventarioLineaEntity>, texto: String): Boolean {
        val t = texto.trim()
        if (t.isEmpty()) return true
        val linea = lineas.firstOrNull {
            it.sku.equals(t, true) || it.codigoBarras.equals(t, true) || it.referencia.equals(t, true)
        }
        if (linea == null) {
            mensaje = "No está en este inventario: $t"
            return false
        }
        mensaje = null
        contar(linea, (linea.contadas ?: 0) + 1)
        return true
    }

    fun cerrar(inventario: InventarioEntity) {
        viewModelScope.launch { dao.cambiarEstado(inventario.id, INVENTARIO_CERRADO, ahora()) }
    }

    fun reabrir(inventario: InventarioEntity) {
        viewModelScope.launch { dao.cambiarEstado(inventario.id, INVENTARIO_ABIERTO, null) }
    }

    /** Escribe un CSV con todas las filas (recuento incluido) y devuelve el fichero; null si falla. */
    suspend fun exportarCsv(context: Context, inventario: InventarioEntity): File? {
        // Igual que en pantalla: sin los artículos sin existencia que no se han contado.
        val lineas = dao.lineas(inventario.id).filter { it.existenciaInicial != 0 || it.bajas > 0 || it.contadas != null }
        val actuales = productoDao.obtenerTodosOrdenados().associate { it.id to it.existencia }
        val carpeta = File(context.getExternalFilesDir(null) ?: context.filesDir, "exportacion/inventarios").also { it.mkdirs() }
        val nombre = "inventario_" + inventario.nombre.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "sin_nombre" } +
            "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".csv"
        fun c(v: String?) = "\"" + (v ?: "").replace("\"", "\"\"") + "\""
        val sb = StringBuilder("sku;referencia;codigo_barras;nombre;categoria;proveedor;existencia_inicial;existencia_actual;bajas;contadas;existencia_al_contar;diferencia\n")
        lineas.forEach { l ->
            val base = l.existenciaAlContar ?: actuales[l.productoId] ?: l.existenciaInicial
            val dif = l.contadas?.let { it - base }
            sb.append(listOf(c(l.sku), c(l.referencia), c(l.codigoBarras), c(l.nombre), c(l.categoria), c(l.proveedor),
                l.existenciaInicial, actuales[l.productoId] ?: "", l.bajas, l.contadas ?: "", l.existenciaAlContar ?: "", dif ?: "").joinToString(";")).append('\n')
        }
        return runCatching { File(carpeta, nombre).also { it.writeText("﻿" + sb.toString(), Charsets.UTF_8) } }.getOrNull()
    }
}
