package com.example.magatzem.ui.categorias

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.CategoriaDao
import com.example.magatzem.data.CategoriaEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val dao: CategoriaDao = db.categoriaDao()
    private val productoDao = db.productoDao()

    val categorias: StateFlow<List<CategoriaEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set

    fun limpiarError() {
        error = null
    }

    /** Mensaje cuando no se puede eliminar una categoría porque aún tiene artículos o subcategorías. */
    var avisoNoEliminable by mutableStateOf<String?>(null)
        private set

    fun limpiarAviso() {
        avisoNoEliminable = null
    }

    private fun mensajeConProductos(categoria: CategoriaEntity, cantidad: Int): String {
        val articulos = if (cantidad == 1) "1 artículo" else "$cantidad artículos"
        return "No se puede eliminar la categoría \"${categoria.nombre}\": tiene $articulos. " +
            "Cambia la categoría de esos artículos o elimínalos antes."
    }

    private fun mensajeConHijas(categoria: CategoriaEntity, cantidad: Int): String {
        val subcategorias = if (cantidad == 1) "1 subcategoría" else "$cantidad subcategorías"
        return "No se puede eliminar la categoría \"${categoria.nombre}\": tiene $subcategorias. " +
            "Muévelas a otra categoría padre o elimínalas antes."
    }

    /** Se llama antes de pedir confirmación: solo si no hay artículos ni subcategorías se continúa con [onPermitido]. */
    fun comprobarEliminar(categoria: CategoriaEntity, onPermitido: () -> Unit) {
        viewModelScope.launch {
            val hijas = dao.contarHijas(categoria.id)
            if (hijas > 0) {
                avisoNoEliminable = mensajeConHijas(categoria, hijas)
                return@launch
            }
            val cantidad = productoDao.contarPorCategoria(categoria.id)
            if (cantidad == 0) onPermitido() else avisoNoEliminable = mensajeConProductos(categoria, cantidad)
        }
    }

    fun crear(nombre: String, parentId: Long?, onSuccess: () -> Unit) {
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) {
            error = "El nombre es obligatorio"
            return
        }
        viewModelScope.launch {
            try {
                dao.insert(CategoriaEntity(nombre = nombreLimpio, parentId = parentId))
                error = null
                onSuccess()
            } catch (e: SQLiteConstraintException) {
                error = "Ya existe una categoría con ese nombre"
            }
        }
    }

    /**
     * `nuevoParentId` puede cambiar respecto al actual: así es como una categoría de primer nivel
     * pasa a ser subcategoría, o una subcategoría pasa a otra categoría padre (o a primer nivel, con
     * `null`). Solo dos niveles: si la categoría tiene subcategorías propias, no se puede convertir
     * en subcategoría de otra (habría que mover antes sus hijas).
     */
    fun actualizar(categoria: CategoriaEntity, nuevoNombre: String, nuevoParentId: Long?, onSuccess: () -> Unit) {
        val nombreLimpio = nuevoNombre.trim()
        if (nombreLimpio.isEmpty()) {
            error = "El nombre es obligatorio"
            return
        }
        if (nuevoParentId == categoria.id) {
            error = "Una categoría no puede ser su propia categoría padre"
            return
        }
        viewModelScope.launch {
            if (nuevoParentId != null && dao.contarHijas(categoria.id) > 0) {
                error = "Tiene subcategorías propias: muévelas antes de convertirla en subcategoría"
                return@launch
            }
            try {
                dao.update(categoria.copy(nombre = nombreLimpio, parentId = nuevoParentId))
                error = null
                onSuccess()
            } catch (e: SQLiteConstraintException) {
                error = "Ya existe una categoría con ese nombre"
            }
        }
    }

    /** Vuelve a comprobar y borra en una sola transacción, por si algo cambió desde la confirmación. */
    fun eliminar(categoria: CategoriaEntity) {
        viewModelScope.launch {
            val bloqueo = db.withTransaction {
                val hijas = dao.contarHijas(categoria.id)
                if (hijas > 0) return@withTransaction mensajeConHijas(categoria, hijas)
                val cantidad = productoDao.contarPorCategoria(categoria.id)
                if (cantidad > 0) return@withTransaction mensajeConProductos(categoria, cantidad)
                dao.delete(categoria)
                null
            }
            if (bloqueo != null) avisoNoEliminable = bloqueo
        }
    }
}
