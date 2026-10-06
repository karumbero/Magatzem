package com.example.magatzem.ui.proveedores

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.ProveedorDao
import com.example.magatzem.data.ProveedorEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProveedorViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val dao: ProveedorDao = db.proveedorDao()
    private val productoDao = db.productoDao()

    val proveedores: StateFlow<List<ProveedorEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set

    fun limpiarError() {
        error = null
    }

    /** Mensaje cuando no se puede eliminar un proveedor porque aún tiene artículos. */
    var avisoNoEliminable by mutableStateOf<String?>(null)
        private set

    fun limpiarAviso() {
        avisoNoEliminable = null
    }

    private fun mensajeConProductos(proveedor: ProveedorEntity, cantidad: Int): String {
        val articulos = if (cantidad == 1) "1 artículo" else "$cantidad artículos"
        return "No se puede eliminar el proveedor \"${proveedor.nombre}\": tiene $articulos. " +
            "Cambia el proveedor de esos artículos o elimínalos antes."
    }

    /** Se llama antes de pedir confirmación: solo si no hay artículos se continúa con [onPermitido]. */
    fun comprobarEliminar(proveedor: ProveedorEntity, onPermitido: () -> Unit) {
        viewModelScope.launch {
            val cantidad = productoDao.contarPorProveedor(proveedor.id)
            if (cantidad == 0) onPermitido() else avisoNoEliminable = mensajeConProductos(proveedor, cantidad)
        }
    }

    fun crear(
        nombre: String,
        comercial: String,
        telefono: String,
        email: String,
        nifCif: String,
        direccion: String,
        codigoPostal: String,
        ciudad: String,
        exentoIva: Boolean = false,
        onSuccess: () -> Unit
    ) {
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) {
            error = "El nombre es obligatorio"
            return
        }
        viewModelScope.launch {
            dao.insert(
                ProveedorEntity(
                    nombre = nombreLimpio,
                    comercial = comercial.trim().ifBlank { null },
                    telefono = telefono.trim().ifBlank { null },
                    email = email.trim().ifBlank { null },
                    nifCif = nifCif.trim().ifBlank { null },
                    direccion = direccion.trim().ifBlank { null },
                    codigoPostal = codigoPostal.trim().ifBlank { null },
                    ciudad = ciudad.trim().ifBlank { null },
                    exentoIva = exentoIva
                )
            )
            error = null
            onSuccess()
        }
    }

    fun actualizar(
        proveedor: ProveedorEntity,
        nombre: String,
        comercial: String,
        telefono: String,
        email: String,
        nifCif: String,
        direccion: String,
        codigoPostal: String,
        ciudad: String,
        exentoIva: Boolean = false,
        onSuccess: () -> Unit
    ) {
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) {
            error = "El nombre es obligatorio"
            return
        }
        viewModelScope.launch {
            dao.update(
                proveedor.copy(
                    nombre = nombreLimpio,
                    comercial = comercial.trim().ifBlank { null },
                    telefono = telefono.trim().ifBlank { null },
                    email = email.trim().ifBlank { null },
                    nifCif = nifCif.trim().ifBlank { null },
                    direccion = direccion.trim().ifBlank { null },
                    codigoPostal = codigoPostal.trim().ifBlank { null },
                    ciudad = ciudad.trim().ifBlank { null },
                    exentoIva = exentoIva
                )
            )
            error = null
            onSuccess()
        }
    }

    /** Vuelve a comprobar y borra en una sola transacción, por si algo cambió desde la confirmación. */
    fun eliminar(proveedor: ProveedorEntity) {
        viewModelScope.launch {
            val cantidad = db.withTransaction {
                productoDao.contarPorProveedor(proveedor.id).also { if (it == 0) dao.delete(proveedor) }
            }
            if (cantidad > 0) avisoNoEliminable = mensajeConProductos(proveedor, cantidad)
        }
    }
}
