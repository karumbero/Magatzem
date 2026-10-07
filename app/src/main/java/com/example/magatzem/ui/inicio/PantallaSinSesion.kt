package com.example.magatzem.ui.inicio

import android.content.Context
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.magatzem.R
import com.example.magatzem.ui.nav.Routes
import com.example.magatzem.ui.sesion.LoginDialog
import com.example.magatzem.ui.sesion.SesionViewModel
import java.io.File

// Colores del logotipo de Sa Platgeta.
private val AzulMarino = Color(0xFF0D3B8E)
private val Turquesa = Color(0xFF1596AD)

/** Carpeta donde se dejan los ficheros de ventas traspasados desde MiTPV y pendientes de procesar. */
fun carpetaRecibidos(context: Context): File =
    File(context.getExternalFilesDir(null) ?: context.filesDir, "recibidos").also { it.mkdirs() }

/** Ficheros de ventas pendientes de procesar (los .json de la carpeta de recibidos). */
fun archivosRecibidos(context: Context): List<File> =
    carpetaRecibidos(context).listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }?.toList().orEmpty()

/**
 * Pantalla de inicio: el logotipo con los accesos Magatzem y Oficina arriba y, debajo del dibujo, Recibir (ficheros de
 * ventas, que se pueden recibir sin iniciar sesión). Magatzem y Oficina piden iniciar sesión si no hay un usuario
 * dentro; quien llega aquí desde la aplicación sin haber cerrado sesión entra directamente.
 */
@Composable
fun PantallaSinSesion(sesion: SesionViewModel, onEntrar: (ruta: String) -> Unit) {
    val context = LocalContext.current
    var mostrarLogin by remember { mutableStateOf(false) }
    var mostrarRecibir by remember { mutableStateOf(false) }
    var mostrarMenuAjustes by remember { mutableStateOf(false) }
    var ajusteAbierto by remember { mutableStateOf<AjusteInicio?>(null) }
    // Nombre del acceso (Magatzem u Oficina) que no se permite al usuario actual; null = ningún aviso.
    var avisoAcceso by remember { mutableStateOf<String?>(null) }
    // A dónde quería ir quien tuvo que iniciar sesión primero.
    var pendiente by remember { mutableStateOf<String?>(null) }

    // De momento los dos accesos son solo para usuarios de nivel 1 (los niveles se definirán más adelante).
    fun acceder(ruta: String) {
        if (!sesion.puedeGestionarUsuarios) {
            avisoAcceso = when (ruta) {
                Routes.OFICINA_INICIO -> "Oficina"
                ACCESO_AJUSTES -> "Ajustes"
                else -> "Magatzem"
            }
        } else if (ruta == ACCESO_AJUSTES) {
            mostrarMenuAjustes = true
        } else {
            onEntrar(ruta)
        }
    }

    // Tras iniciar sesión (y cambiar el PIN si hacía falta) se continúa hacia donde se iba.
    LaunchedEffect(sesion.nombre, pendiente) {
        val destino = pendiente
        if (sesion.nombre != null && destino != null) {
            pendiente = null
            mostrarLogin = false
            acceder(destino)
        }
    }

    fun pulsar(ruta: String) {
        if (sesion.nombre != null) acceder(ruta) else { pendiente = ruta; mostrarLogin = true }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BotonInicio("Magatzem", AzulMarino, Modifier.weight(1f)) { pulsar(Routes.MAGATZEM_INICIO) }
            BotonInicio("Oficina", Turquesa, Modifier.weight(1f)) { pulsar(Routes.OFICINA_INICIO) }
        }
        Image(
            painter = painterResource(R.drawable.saplatgeta),
            contentDescription = "Sa Platgeta, artesanía y regalos, Cala Ratjada",
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp)
        )
        BotonInicio("Recibir", Turquesa, Modifier.width(260.dp)) { mostrarRecibir = true }
    }
    // Engranaje abajo a la izquierda: usuarios, empresa, formas de pago y bancos (solo nivel 1).
    Box(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
        IconButton(onClick = { pulsar(ACCESO_AJUSTES) }) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = AzulMarino, modifier = Modifier.size(32.dp))
        }
        DropdownMenu(expanded = mostrarMenuAjustes, onDismissRequest = { mostrarMenuAjustes = false }) {
            listOf(
                "Usuarios" to AjusteInicio.USUARIOS,
                "Datos de empresa" to AjusteInicio.DATOS_EMPRESA,
                "Formas de pago" to AjusteInicio.FORMAS_PAGO,
                "Bancos" to AjusteInicio.BANCOS,
                "Caja inicial" to AjusteInicio.CAJA_INICIAL
            ).forEach { (texto, ajuste) ->
                DropdownMenuItem(text = { Text(texto) }, onClick = { mostrarMenuAjustes = false; ajusteAbierto = ajuste })
            }
        }
    }
    }

    ajusteAbierto?.let { AjustesInicioDialog(it, onCerrar = { ajusteAbierto = null }) }
    if (mostrarLogin) {
        LoginDialog(sesion, onCancelar = { mostrarLogin = false; pendiente = null })
    }
    avisoAcceso?.let { acceso ->
        AlertDialog(
            onDismissRequest = { avisoAcceso = null },
            title = { Text(acceso) },
            text = { Text("$acceso es solo para usuarios de nivel 1.") },
            confirmButton = { TextButton(onClick = { avisoAcceso = null }) { Text("Aceptar") } }
        )
    }
    if (mostrarRecibir) {
        val archivos = remember { archivosRecibidos(context) }
        AlertDialog(
            onDismissRequest = { mostrarRecibir = false },
            title = { Text("Recibir") },
            text = {
                Text(
                    if (archivos.isEmpty()) "No hay archivo de ventas a procesar."
                    else if (archivos.size == 1) "Hay 1 archivo de ventas a procesar."
                    else "Hay ${archivos.size} archivos de ventas a procesar."
                )
            },
            confirmButton = {
                // De momento Procesar no hace nada más que cerrar: el proceso de los ficheros llegará más adelante.
                if (archivos.isNotEmpty()) TextButton(onClick = { mostrarRecibir = false }) { Text("Procesar") }
            },
            dismissButton = { TextButton(onClick = { mostrarRecibir = false }) { Text("Cerrar") } }
        )
    }
}

/** Marca interna para el engranaje (no es una ruta): pide sesión y nivel 1 como los demás accesos. */
private const val ACCESO_AJUSTES = "ajustes_inicio"

@Composable
private fun BotonInicio(texto: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Text(texto, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
