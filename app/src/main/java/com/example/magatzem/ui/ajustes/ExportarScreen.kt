package com.example.magatzem.ui.ajustes

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.ui.common.formatearFecha

/**
 * Exporta el catálogo al programa de ventas y abre el selector de Android para enviarlo (Quick
 * Share, Google Drive, Gmail, lo que el usuario elija — la app no fuerza ningún transporte). La
 * primera vez (la apertura) manda usuarios, datos de empresa, formas de pago y bancos, proveedores,
 * categorías y todos los artículos, y borra las entradas; las siguientes, solo los artículos nuevos o modificados desde la última exportación.
 */
@Composable
fun ExportarScreen(viewModel: ExportarViewModel = viewModel()) {
    val context = LocalContext.current
    val pendientes by viewModel.pendientes.collectAsState()
    val ultimaExportacion by viewModel.ultimaExportacion.collectAsState()
    var mensaje by rememberSaveable { mutableStateOf<String?>(null) }
    val cambiosEnMaestros by viewModel.cambiosEnMaestros.collectAsState()
    val esPrimera = ultimaExportacion == null
    val (recepciones, albaranes, facturas) = viewModel.entradasABorrar.collectAsState().value
    val facturasPendientes = viewModel.facturasPendientes.collectAsState().value
    var confirmarPrimera by rememberSaveable { mutableStateOf(false) }

    fun compartir(archivo: java.io.File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, archivo.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Enviar catálogo a MiTPV"))
    }

    fun exportar() {
        viewModel.exportar(
            onExito = { archivo ->
                mensaje = null
                compartir(archivo)
            },
            onNada = { mensaje = "No hay nada pendiente de exportar" }
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.padding(24.dp).widthIn(max = 480.dp)) {
            Text(text = "Exportar al programa de ventas", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (esPrimera)
                    "Todavía no se ha exportado nunca. La primera exportación (la apertura) manda " +
                        "usuarios, datos de empresa, formas de pago y bancos, proveedores, categorías y " +
                        "todos los artículos (sin existencias), y borra las entradas registradas " +
                        "(recepciones, albaranes y facturas con sus pagos). Desde ahí todo parte de cero: la existencia " +
                        "actual de cada artículo queda como una sola capa a su coste actual."
                else
                    "Artículos nuevos o modificados que el programa de ventas todavía no tiene, más " +
                        "los proveedores y categorías actuales, y los borrados (lo que ya no exista aquí se " +
                        "borra allí). Nunca se manda existencia: eso lo lleva Magatzem.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (pendientes == 0) {
                    if (cambiosEnMaestros) "Hay cambios o borrados en categorías, proveedores o artículos"
                    else "No hay artículos pendientes de exportar"
                } else
                    "$pendientes artículo${if (pendientes == 1) "" else "s"} pendiente${if (pendientes == 1) "" else "s"} de exportar",
                style = MaterialTheme.typography.titleMedium
            )
            ultimaExportacion?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Última exportación: ${formatearFecha(it.fecha)} · ${it.cantidadArticulos} artículos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (viewModel.exportando) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { if (esPrimera) confirmarPrimera = true else exportar() }, enabled = pendientes > 0 || esPrimera || cambiosEnMaestros) {
                    Text("Exportar")
                }
            }
            if (ultimaExportacion != null && !viewModel.exportando) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val archivo = viewModel.archivoUltimaExportacion()
                    if (archivo != null) { mensaje = null; compartir(archivo) }
                    else mensaje = "No se encuentra el fichero de la última exportación"
                }) { Text("Enviar última exportación") }
            }
            mensaje?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (confirmarPrimera) {
        AlertDialog(
            onDismissRequest = { confirmarPrimera = false },
            title = { Text("Primera exportación (apertura)") },
            text = {
                Text(
                    "Además de exportar, se BORRARÁN las entradas registradas: " +
                        "$recepciones ${if (recepciones == 1) "recepción" else "recepciones"}, " +
                        "$albaranes albarán${if (albaranes == 1) "" else "es"} y " +
                        "$facturas factura${if (facturas == 1) "" else "s"} (con sus pagos). " +
                        (if (facturasPendientes > 0)
                            "ATENCIÓN: $facturasPendientes ${if (facturasPendientes == 1) "factura está pendiente" else "facturas están pendientes"} de pago y también se borrarán. "
                        else "") +
                        "La existencia de los artículos no cambia: pasa a ser una única capa de coste inicial por artículo, a su coste actual. No se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarPrimera = false
                    exportar()
                }) { Text("Exportar y borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmarPrimera = false }) { Text("Cancelar") } }
        )
    }
}
