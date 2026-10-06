package com.example.magatzem.ui.oficina

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.FormaPagoEntity
import com.example.magatzem.data.MovimientoBancoEntity
import com.example.magatzem.data.MovimientoCajaEntity
import com.example.magatzem.data.esBanco
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Oficina → Caja y Bancos: saldo y movimientos del efectivo de la tienda y de cada cuenta bancaria. */
class CajaBancosViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database

    val movimientosCaja: StateFlow<List<MovimientoCajaEntity>> = db.movimientoDao().observeCaja()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movimientosBanco: StateFlow<List<MovimientoBancoEntity>> = db.movimientoDao().observeBanco()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bancos: StateFlow<List<FormaPagoEntity>> = db.formaPagoDao().observeAll()
        .map { lista -> lista.filter { it.esBanco() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
