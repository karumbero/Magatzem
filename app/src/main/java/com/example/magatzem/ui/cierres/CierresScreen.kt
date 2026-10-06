package com.example.magatzem.ui.cierres

import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.CierreImportadoEntity
import com.example.magatzem.ui.common.formatearFecha

/** El mismo ViewModel para toda la Activity (lo usa también "Compartir" desde MiTPV). */
@Composable
fun rememberCierreImportViewModel(): CierreImportViewModel {
    val activity = LocalActivity.current as ComponentActivity
    return viewModel(viewModelStoreOwner = activity)
}

/** Ajustes → Importar: cierres de caja de MiTPV ya importados, y el botón para importar otro. */
@Composable
fun CierresScreen(viewModel: CierreImportViewModel = rememberCierreImportViewModel()) {
    val cierres by viewModel.cierres.collectAsState()
    // "*/*" porque los .json suelen llegar como application/octet-stream y el selector los ocultaría.
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.cargar(uri)
    }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Importar cierres de caja", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { selector.launch(arrayOf("*/*")) }) { Text("Importar cierre") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        if (cierres.isEmpty()) {
            Text(
                "Todavía no se ha importado ningún cierre. Se importa desde \"Compartir\" en MiTPV o con el botón de arriba.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(cierres, key = { it.id }) { FilaCierre(it) }
            }
        }
    }
}

@Composable
private fun FilaCierre(c: CierreImportadoEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "${formatearFecha(c.cerradoEn)} · ${c.cajeroNombre} · ${c.numTickets} ticket(s)",
            style = MaterialTheme.typography.titleMedium
        )
        val dif = if (c.diferencia == 0.0) "cuadra" else if (c.diferencia > 0) "sobrante %.2f €".format(c.diferencia) else "faltante %.2f €".format(-c.diferencia)
        Text(
            "Contado %.2f € · Tarjeta %.2f € · Retirado a banco %.2f € · Arqueo: %s".format(c.ventaContado, c.ventaTarjeta, c.retiradoBanco, dif),
            style = MaterialTheme.typography.bodyMedium
        )
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

/** Avisos del importador; viven a nivel de navegación para verse también cuando llega un fichero por "Compartir". */
@Composable
fun CierreImportDialogs(viewModel: CierreImportViewModel = rememberCierreImportViewModel()) {
    if (viewModel.trabajando) {
        AlertDialog(onDismissRequest = {}, title = { Text("Importando…") }, text = { CircularProgressIndicator() }, confirmButton = {})
    }
    viewModel.aviso?.let { (titulo, mensaje) ->
        AlertDialog(
            onDismissRequest = viewModel::descartarAviso,
            title = { Text(titulo) },
            text = { Text(mensaje) },
            confirmButton = { TextButton(onClick = viewModel::descartarAviso) { Text("Aceptar") } }
        )
    }
}
