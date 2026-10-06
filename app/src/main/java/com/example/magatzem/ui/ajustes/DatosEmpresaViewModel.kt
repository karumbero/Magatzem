package com.example.magatzem.ui.ajustes

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.DatosEmpresaEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DatosEmpresaViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as MagatzemApplication).database.datosEmpresaDao()

    val datos: StateFlow<DatosEmpresaEntity?> = dao.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var error by mutableStateOf<String?>(null)
        private set

    fun guardar(
        nombre: String,
        nombreComercial: String,
        nifCif: String,
        direccion: String,
        codigoPostal: String,
        ciudad: String,
        telefono: String,
        email: String,
        onSuccess: () -> Unit
    ) {
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) {
            error = "El nombre es obligatorio"
            return
        }
        viewModelScope.launch {
            dao.guardar(
                DatosEmpresaEntity(
                    nombre = nombreLimpio,
                    nombreComercial = nombreComercial.trim().ifBlank { null },
                    nifCif = nifCif.trim().ifBlank { null },
                    direccion = direccion.trim().ifBlank { null },
                    codigoPostal = codigoPostal.trim().ifBlank { null },
                    ciudad = ciudad.trim().ifBlank { null },
                    telefono = telefono.trim().ifBlank { null },
                    email = email.trim().ifBlank { null }
                )
            )
            error = null
            onSuccess()
        }
    }
}
