package com.example.magatzem.ui.ajustes

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.FormaPagoDao
import com.example.magatzem.data.FormaPagoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FormaPagoViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: FormaPagoDao = (application as MagatzemApplication).database.formaPagoDao()

    val formasPago: StateFlow<List<FormaPagoEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set

    fun limpiarError() {
        error = null
    }

    fun crear(nombre: String, onSuccess: () -> Unit) {
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) {
            error = "El nombre es obligatorio"
            return
        }
        viewModelScope.launch {
            try {
                dao.insert(FormaPagoEntity(nombre = nombreLimpio))
                error = null
                onSuccess()
            } catch (e: SQLiteConstraintException) {
                error = "Ya existe una forma de pago con ese nombre"
            }
        }
    }

    fun actualizar(formaPago: FormaPagoEntity, nuevoNombre: String, activo: Boolean, onSuccess: () -> Unit) {
        val nombreLimpio = nuevoNombre.trim()
        if (nombreLimpio.isEmpty()) {
            error = "El nombre es obligatorio"
            return
        }
        viewModelScope.launch {
            try {
                dao.update(formaPago.copy(nombre = nombreLimpio, activo = activo))
                error = null
                onSuccess()
            } catch (e: SQLiteConstraintException) {
                error = "Ya existe una forma de pago con ese nombre"
            }
        }
    }
}
