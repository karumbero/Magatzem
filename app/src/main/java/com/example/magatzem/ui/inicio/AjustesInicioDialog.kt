package com.example.magatzem.ui.inicio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.launch
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
enum class AjusteInicio { USUARIOS, DATOS_EMPRESA, FORMAS_PAGO, BANCOS, CAJA_INICIAL }

@Composable
fun AjustesInicioDialog(ajuste: AjusteInicio, onCerrar: () -> Unit) {
    // La caja inicial es un diálogo pequeño, no una ventana grande.
    if (ajuste == AjusteInicio.CAJA_INICIAL) {
        CajaInicialDialog(onCerrar)
        return
    }
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
                        AjusteInicio.CAJA_INICIAL -> Unit
                    }
                }
            }
        }
    }
}

/**
 * Caja inicial: el importe con el que se abre la caja cada día (fondo de caja). Se guarda en la base de datos de Magatzem; con 0 o vacío
 * no hay caja inicial fija.
 */
@Composable
private fun CajaInicialDialog(onCerrar: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { (context.applicationContext as com.example.magatzem.MagatzemApplication).database.ajusteDao() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        texto = dao.valor(com.example.magatzem.data.AJUSTE_CAJA_INICIAL)?.toDoubleOrNull()?.let { "%.2f".format(it).replace('.', ',') } ?: ""
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Caja inicial") },
        text = {
            Column {
                Text("Importe con el que se abre la caja cada día.")
                Spacer(modifier = Modifier.height(8.dp))
                com.example.magatzem.ui.common.LabeledTextField(
                    label = "Importe (€)",
                    value = texto,
                    onValueChange = { texto = it; error = null },
                    isError = error != null,
                    supportingText = error?.let { e -> { Text(e) } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val limpio = texto.trim().replace(',', '.')
                val importe = if (limpio.isEmpty()) 0.0 else limpio.toDoubleOrNull()
                if (importe == null || importe < 0) error = "Importe no válido"
                else scope.launch {
                    dao.guardar(com.example.magatzem.data.AJUSTE_CAJA_INICIAL, importe.toString())
                    onCerrar()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}
