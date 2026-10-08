package com.example.magatzem.ui.usuarios

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.PIN_POR_DEFECTO
import com.example.magatzem.data.UsuarioDao
import com.example.magatzem.data.UsuarioEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UsuarioViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: UsuarioDao = (application as MagatzemApplication).database.usuarioDao()

    val usuarios: StateFlow<List<UsuarioEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set

    fun limpiarError() {
        error = null
    }

    private fun validarNombre(nombre: String): String? =
        if (nombre.isEmpty()) "El nombre es obligatorio" else null

    private fun validarNivel(nivel: String): String? =
        if (nivel.length != 1 || nivel.any { !it.isDigit() }) "El nivel debe ser 1 dígito" else null

    private fun validarPin(pin: String): String? =
        if (pin.length != 6 || pin.any { !it.isDigit() }) "El PIN debe tener 6 dígitos" else null

    private fun validar(nombre: String, pin: String, nivel: String): String? =
        validarNombre(nombre) ?: validarPin(pin) ?: validarNivel(nivel)

    /** Los usuarios nuevos siempre se crean con el PIN por defecto; se les obliga a cambiarlo al entrar. */
    fun crear(nombre: String, nivel: String, onSuccess: () -> Unit) {
        val nombreLimpio = nombre.trim()
        val nivelLimpio = nivel.trim()
        val mensaje = validarNombre(nombreLimpio) ?: validarNivel(nivelLimpio)
        if (mensaje != null) {
            error = mensaje
            return
        }
        viewModelScope.launch {
            dao.insert(
                UsuarioEntity(
                    nombre = nombreLimpio,
                    pin = PIN_POR_DEFECTO,
                    nivel = nivelLimpio.toInt(),
                    debeCambiarPin = true
                )
            )
            error = null
            onSuccess()
        }
    }

    fun actualizar(usuario: UsuarioEntity, nombre: String, pin: String, nivel: String, onSuccess: () -> Unit) {
        val nombreLimpio = nombre.trim()
        val pinLimpio = pin.trim()
        val nivelLimpio = nivel.trim()
        val mensaje = validar(nombreLimpio, pinLimpio, nivelLimpio)
        if (mensaje != null) {
            error = mensaje
            return
        }
        viewModelScope.launch {
            dao.update(usuario.copy(nombre = nombreLimpio, pin = pinLimpio, nivel = nivelLimpio.toInt()))
            error = null
            onSuccess()
        }
    }

    /** Los usuarios de nivel 1 no se borran (se sincronizan con MiTPV): se desactivan. */
    fun eliminar(usuario: UsuarioEntity) {
        if (usuario.nivel == 1) {
            error = "Los usuarios de nivel 1 no se borran: se desactivan"
            return
        }
        viewModelScope.launch { dao.delete(usuario) }
    }

    /** Activa o desactiva. Tiene que quedar siempre algún usuario activo de nivel 1. */
    fun cambiarActivo(usuario: UsuarioEntity, onSuccess: () -> Unit) {
        if (usuario.activo && usuario.nivel == 1 && usuarios.value.none { it.id != usuario.id && it.activo && it.nivel == 1 }) {
            error = "Tiene que quedar al menos un usuario activo de nivel 1"
            return
        }
        viewModelScope.launch {
            dao.update(usuario.copy(activo = !usuario.activo))
            error = null
            onSuccess()
        }
    }
}
