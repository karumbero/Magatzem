package com.example.magatzem.ui.inicio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.magatzem.ui.ajustes.DatosEmpresaScreen
import com.example.magatzem.ui.ajustes.FormaPagoListadoScreen
import com.example.magatzem.ui.oficina.BancoScreen
import com.example.magatzem.ui.usuarios.UsuarioAltaScreen
import com.example.magatzem.ui.usuarios.UsuarioListadoScreen

/** Opciones del engranaje de la pantalla de inicio: se abren como ventana modal sobre ella (no son pantallas de la app). */
enum class AjusteInicio { USUARIOS, DATOS_EMPRESA, FORMAS_PAGO, BANCOS }

@Composable
fun AjustesInicioDialog(ajuste: AjusteInicio, onCerrar: () -> Unit) {
    // Dentro de Usuarios, "Añadir +" cambia la misma ventana al formulario de alta.
    var altaUsuario by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.9f)
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCerrar) { Text("Cerrar") }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (ajuste) {
                        AjusteInicio.USUARIOS ->
                            if (altaUsuario) UsuarioAltaScreen(onCancelar = { altaUsuario = false })
                            else UsuarioListadoScreen(onAnadir = { altaUsuario = true })
                        AjusteInicio.DATOS_EMPRESA -> DatosEmpresaScreen(onGuardado = onCerrar)
                        AjusteInicio.FORMAS_PAGO -> FormaPagoListadoScreen()
                        AjusteInicio.BANCOS -> BancoScreen()
                    }
                }
            }
        }
    }
}
