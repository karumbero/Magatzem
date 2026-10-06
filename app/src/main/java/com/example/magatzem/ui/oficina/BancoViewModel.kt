package com.example.magatzem.ui.oficina

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
import com.example.magatzem.data.NOMBRE_CONTADO
import com.example.magatzem.data.NOMBRE_TRANSFERENCIA
import com.example.magatzem.data.esBanco
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Datos (IBAN/titular) de las formas de pago que son cuentas bancarias, no del catálogo entero. */
class BancoViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: FormaPagoDao = (application as MagatzemApplication).database.formaPagoDao()

    val bancos: StateFlow<List<FormaPagoEntity>> = dao.observeAll()
        .map { lista -> lista.filter { it.esBanco() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun actualizar(formaPago: FormaPagoEntity, iban: String, titular: String) {
        viewModelScope.launch {
            dao.update(
                formaPago.copy(
                    iban = iban.trim().ifBlank { null },
                    titular = titular.trim().ifBlank { null }
                )
            )
        }
    }

    /** Lo marca como el banco que recibe tarjeta e ingresos de caja al importar cierres. */
    fun marcarPrincipal(formaPago: FormaPagoEntity) {
        viewModelScope.launch { dao.marcarPrincipal(formaPago.id) }
    }

    var error by mutableStateOf<String?>(null)
        private set

    fun limpiarError() {
        error = null
    }

    /** Alta de un banco nuevo: es una forma de pago más (ni Contado ni Transferencia) con IBAN y titular. */
    fun crear(nombre: String, iban: String, titular: String, onSuccess: () -> Unit) {
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) {
            error = "El nombre es obligatorio"
            return
        }
        if (nombreLimpio == NOMBRE_CONTADO || nombreLimpio == NOMBRE_TRANSFERENCIA) {
            error = "Ese nombre está reservado: no es un banco"
            return
        }
        viewModelScope.launch {
            try {
                dao.insert(
                    FormaPagoEntity(
                        nombre = nombreLimpio,
                        iban = iban.trim().ifBlank { null },
                        titular = titular.trim().ifBlank { null }
                    )
                )
                error = null
                onSuccess()
            } catch (e: SQLiteConstraintException) {
                error = "Ya existe un banco o forma de pago con ese nombre"
            }
        }
    }
}
