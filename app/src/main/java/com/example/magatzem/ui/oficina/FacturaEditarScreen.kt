package com.example.magatzem.ui.oficina

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.factorCoste
import com.example.magatzem.data.factorIva
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown
import com.example.magatzem.ui.common.fechaDocumento

private fun euros(valor: Double) = "%.2f €".format(valor)

/**
 * Oficina → Facturas → un factura abierto: se puede cambiar la cantidad y el coste de cada línea, quitar líneas y
 * añadir artículos del mismo proveedor. Al guardar, los cambios se aplican a los artículos (existencia, coste y PVP).
 */
@Composable
fun FacturaEditarScreen(facturaId: Long, onTerminado: () -> Unit, viewModel: FacturaEditarViewModel = viewModel()) {
    LaunchedEffect(facturaId) { viewModel.cargar(facturaId) }
    val articulos by viewModel.articulosProveedor.collectAsState()
    val factura = viewModel.factura

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Factura ${factura?.numero ?: "—"}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${viewModel.proveedor?.nombre ?: "Sin proveedor"}" + (factura?.let { " · ${fechaDocumento(it.fecha)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            OutlinedButton(onClick = onTerminado) { Text("Cancelar") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (!viewModel.cargado) {
            Text("Cargando…")
            return@Column
        }

        SelectorDropdown(
            label = "Añadir artículo del proveedor",
            opciones = articulos.map { it.id as Long? to "${it.sku ?: "—"} · ${it.nombre}" },
            seleccionado = null,
            onSeleccionar = { id -> articulos.firstOrNull { it.id == id }?.let(viewModel::anadir) },
            modifier = Modifier.width(420.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(viewModel.lineas.toList(), key = { it.producto.id }) { linea ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${linea.producto.sku ?: "—"} · ${linea.producto.nombre}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Existencia ${linea.producto.existencia}" + if (linea.cantidadOriginal > 0) " · recibidas ${linea.cantidadOriginal}" else " · línea nueva",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LabeledTextField(
                        label = "Cantidad", value = linea.cantidad, onValueChange = { linea.cantidad = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, compacto = true,
                        modifier = Modifier.width(110.dp)
                    )
                    LabeledTextField(
                        label = "Coste (base)", value = linea.coste, onValueChange = { linea.coste = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, compacto = true,
                        modifier = Modifier.width(130.dp)
                    )
                    IconButton(onClick = { viewModel.quitar(linea) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Quitar línea", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
        }

        val base = viewModel.baseActual()
        val exento = viewModel.exento
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Base ${euros(base)} · IVA y recargo ${euros(base * (factorCoste(exento) - 1))} · Total con IVA y recargo ${euros(base * factorCoste(exento))}",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            "Al guardar, la existencia de cada artículo sube o baja según lo que cambies (quitar una línea resta lo que había entrado) " +
                "y un coste distinto actualiza el coste y el PVP del artículo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.guardar(onTerminado) }) { Text("Guardar cambios") }
    }
}
