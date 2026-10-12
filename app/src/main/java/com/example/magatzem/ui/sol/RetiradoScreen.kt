package com.example.magatzem.ui.sol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.RetiradaEntity
import com.example.magatzem.data.RetiradaLineaEntity

private fun fechaCorta(f: String): String =
    runCatching { f.substring(8, 10) + "-" + f.substring(5, 7) + "-" + f.substring(0, 4) + " " + f.substring(11, 16) }.getOrDefault(f)

/** Retirado: las retiradas ya guardadas, de la más reciente a la más antigua; al pulsar una se ven sus líneas. */
@Composable
fun RetiradoScreen(viewModel: RetirarViewModel = viewModel()) {
    val retiradas by viewModel.retiradas.collectAsState()
    var abierta by remember { mutableStateOf<RetiradaEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Retirado", style = MaterialTheme.typography.headlineSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        if (retiradas.isEmpty()) {
            Text("Todavía no hay ninguna retirada guardada.")
        } else {
            LazyColumn {
                items(retiradas, key = { it.id }) { r ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { abierta = r }.padding(vertical = 12.dp)) {
                        Text(fechaCorta(r.fecha), modifier = Modifier.weight(1f))
                        Text(euros(r.total), fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    abierta?.let { r ->
        var lineas by remember(r.id) { mutableStateOf<List<RetiradaLineaEntity>>(emptyList()) }
        LaunchedEffect(r.id) { lineas = viewModel.lineasDe(r.id) }
        AlertDialog(
            onDismissRequest = { abierta = null },
            title = { Text("Retirada del ${fechaCorta(r.fecha)}") },
            text = {
                Column(modifier = Modifier.width(560.dp)) {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(lineas, key = { it.id }) { l ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(listOfNotNull(l.sku, l.nombre).joinToString(" · "), modifier = Modifier.weight(1f))
                                Text("${l.cantidad} × ${euros(l.pvp)}", modifier = Modifier.padding(horizontal = 8.dp))
                                Text(euros(l.importe), fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Total", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(euros(r.total), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { abierta = null }) { Text("Cerrar") } }
        )
    }
}
