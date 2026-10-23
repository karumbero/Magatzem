package com.example.magatzem.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.magatzem.data.tpv.SincronizacionTpv
import com.example.magatzem.data.tpv.TpvError
import com.example.magatzem.data.tpv.TpvNoAutorizado
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ajustes → Sincronizar con TPV: manda los datos de Magatzem (generales y de Almacén) al TPV, en un solo sentido. */
@Composable
fun SincronizarTpvDialog(onCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var direccion by remember { mutableStateOf(SincronizacionTpv.direccion(contexto)) }
    var pin by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var codigo by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var resumen by remember { mutableStateOf<String?>(null) }

    fun texto(e: Exception): String = when (e) {
        is TpvNoAutorizado, is TpvError -> e.message.orEmpty()
        is java.net.SocketTimeoutException, is java.net.ConnectException, is java.net.UnknownHostException, is java.net.NoRouteToHostException ->
            "No se llega al TPV (${SincronizacionTpv.direccion(contexto)}). Comprueba la wifi y la dirección."
        else -> "Error: ${e.message ?: e.javaClass.simpleName}"
    }

    AlertDialog(
        onDismissRequest = { if (!ocupado) onCerrar() },
        title = { Text("Sincronizar con el TPV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Manda al TPV los datos de Magatzem (artículos, proveedores, categorías, entradas, albaranes, facturas, pedidos y mermas). En un solo sentido: el TPV sustituye " +
                        "esos datos por los de aquí. No toca ventas, cierres, horarios ni gastos.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(value = direccion, onValueChange = { direccion = it }, singleLine = true, label = { Text("Dirección del TPV") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !ocupado, onClick = {
                        SincronizacionTpv.guardarDireccion(contexto, direccion)
                        alcance.launch { mensaje = if (SincronizacionTpv.alcanzable(contexto)) "El TPV responde." else "No se llega al TPV en ${SincronizacionTpv.direccion(contexto)}." }
                    }) { Text("Probar conexión") }
                    OutlinedButton(enabled = !ocupado, onClick = {
                        SincronizacionTpv.guardarDireccion(contexto, direccion)
                        alcance.launch {
                            try {
                                val (id, c) = SincronizacionTpv.pedirAutorizacion(contexto, android.os.Build.MODEL ?: "Magatzem")
                                codigo = c; mensaje = "En el TPV: Ajustes → Aparatos → Autorizar la petición con el código $c."
                                repeat(400) {
                                    when (SincronizacionTpv.estadoAutorizacion(contexto, id)) {
                                        "autorizado" -> { codigo = null; mensaje = "Aparato autorizado."; return@launch }
                                        "rechazado" -> { codigo = null; mensaje = "Se ha rechazado la petición."; return@launch }
                                        "caducado" -> { codigo = null; mensaje = "La petición ha caducado. Pídela otra vez."; return@launch }
                                    }
                                    delay(1500)
                                }
                            } catch (e: TpvError) { codigo = null; mensaje = if (e.codigo == 404) "Este aparato no necesita autorización." else e.message }
                            catch (e: Exception) { codigo = null; mensaje = texto(e) }
                        }
                    }) { Text("Autorizar este aparato") }
                }
                codigo?.let { Text(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, singleLine = true, label = { Text("PIN de un usuario de nivel 1 del TPV") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()
                )
                if (ocupado) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(modifier = Modifier.padding(2.dp)); Text("Sincronizando… no cierres la app.") }
                mensaje?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (it.startsWith("Sincronizado")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                resumen?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !ocupado && pin.length == 6, onClick = {
                SincronizacionTpv.guardarDireccion(contexto, direccion)
                alcance.launch {
                    ocupado = true; mensaje = null; resumen = null
                    try {
                        SincronizacionTpv.entrar(contexto, pin)
                        val r = SincronizacionTpv.sincronizar(contexto)
                        val tablas = r.optJSONObject("tablas")
                        mensaje = "Sincronizado con el TPV. Copia de seguridad del TPV: ${r.optString("copia")}."
                        resumen = tablas?.keys()?.asSequence()?.joinToString(" · ") { "$it ${tablas.get(it)}" }
                        pin = ""
                    } catch (e: Exception) { mensaje = texto(e) }
                    finally { ocupado = false }
                }
            }) { Text("Sincronizar") }
        },
        dismissButton = { TextButton(enabled = !ocupado, onClick = onCerrar) { Text("Cerrar") } }
    )
}
