package com.example.magatzem.ui.sesion

import androidx.activity.compose.LocalActivity
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.SesionManager
import com.example.magatzem.data.UsuarioDao
import com.example.magatzem.data.UsuarioEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A diferencia de BackShop, aquí no hace falta un asistente de "crea el primer usuario / datos de
 * empresa / banco" al primer arranque: la base de datos ya se entrega sembrada (usuaria Mar, las 3
 * formas de pago, la empresa) desde `AppDatabase`. Esta clase solo gestiona el login normal.
 */
class SesionViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: UsuarioDao = (application as MagatzemApplication).database.usuarioDao()
    private val sesion = SesionManager(application)

    var nombre by mutableStateOf(sesion.obtenerNombre())
        private set
    var nivel by mutableStateOf(sesion.obtenerNivel())
        private set

    /** Para el desplegable del login: puede haber varios usuarios con el mismo PIN por defecto. */
    val usuarios: StateFlow<List<UsuarioEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set

    /** Se está mostrando la pantalla de inicio (logotipo) en vez de la aplicación; se puede volver a ella sin cerrar sesión. */
    var enInicio by mutableStateOf(true)

    fun irAInicio() {
        enInicio = true
    }

    /** Si no es null, hay que cambiarle el PIN por defecto antes de dejarle entrar. */
    var usuarioPendienteCambioPin by mutableStateOf<UsuarioEntity?>(null)
        private set

    val requierePin: Boolean
        get() = nombre == null

    /** Nivel 1 puede gestionar usuarios, oficina y ajustes. */
    val puedeGestionarUsuarios: Boolean
        get() = nivel == 1

    fun iniciarSesion(usuarioId: Long?, pin: String) {
        if (usuarioId == null) {
            error = "Selecciona tu usuario"
            return
        }
        viewModelScope.launch {
            val usuario = dao.obtenerPorId(usuarioId)
            when {
                usuario == null || usuario.pin != pin.trim() -> error = "PIN incorrecto"
                usuario.debeCambiarPin -> {
                    usuarioPendienteCambioPin = usuario
                    error = null
                }
                else -> completarLogin(usuario)
            }
        }
    }

    /** Se llama tras un PIN correcto (directo, o justo después de cambiar el PIN por defecto). */
    private fun completarLogin(usuario: UsuarioEntity) {
        sesion.guardar(usuario.nombre, usuario.nivel)
        nombre = usuario.nombre
        nivel = usuario.nivel
        error = null
        usuarioPendienteCambioPin = null
    }

    fun cambiarPin(nuevoPin: String, confirmarPin: String) {
        val usuario = usuarioPendienteCambioPin ?: return
        if (nuevoPin.length != 6 || nuevoPin.any { !it.isDigit() }) {
            error = "El PIN debe tener 6 dígitos"
            return
        }
        if (nuevoPin != confirmarPin) {
            error = "Los dos PIN no coinciden"
            return
        }
        viewModelScope.launch {
            val actualizado = usuario.copy(pin = nuevoPin, debeCambiarPin = false)
            dao.update(actualizado)
            completarLogin(actualizado)
        }
    }

    fun limpiarError() {
        error = null
    }

    fun cerrarSesion() {
        sesion.borrar()
        nombre = null
        nivel = null
    }
}

/**
 * Siempre la misma instancia para toda la app (ligada a la Activity), aunque se pida desde una
 * pantalla dentro del NavHost, donde [viewModel] por defecto la ligaría solo a esa pantalla.
 */
@Composable
fun rememberSesionViewModel(): SesionViewModel {
    val activity = LocalActivity.current as ComponentActivity
    return viewModel(viewModelStoreOwner = activity)
}
