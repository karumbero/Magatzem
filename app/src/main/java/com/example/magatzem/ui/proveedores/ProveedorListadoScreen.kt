package com.example.magatzem.ui.proveedores

import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.ui.common.AvisoDialog
import com.example.magatzem.ui.common.ConfirmDeleteDialog
import com.example.magatzem.ui.common.LabeledTextField

@Composable
fun ProveedorListadoScreen(viewModel: ProveedorViewModel = viewModel(), onAnadir: () -> Unit = {}) {
    val proveedores by viewModel.proveedores.collectAsState()
    var proveedorEditando by remember { mutableStateOf<ProveedorEntity?>(null) }
    var proveedorEliminando by remember { mutableStateOf<ProveedorEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Proveedores", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onAnadir) { Text("Añadir +") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(proveedores, key = { it.id }) { proveedor ->
                val detalle = listOfNotNull(proveedor.comercial, proveedor.telefono, proveedor.email)
                    .joinToString(" - ")
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("${proveedor.id}")
                        }
                        append("  ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(proveedor.nombre)
                        }
                        if (detalle.isNotEmpty()) {
                            append("  ")
                            append(detalle)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { proveedorEditando = proveedor }
                        .padding(vertical = 12.dp)
                )
                HorizontalDivider()
            }
        }
    }

    proveedorEditando?.let { proveedor ->
        EditarProveedorDialog(
            proveedor = proveedor,
            viewModel = viewModel,
            onDismiss = {
                proveedorEditando = null
                viewModel.limpiarError()
            },
            onEliminar = {
                proveedorEditando = null
                viewModel.comprobarEliminar(proveedor) { proveedorEliminando = proveedor }
            }
        )
    }

    viewModel.avisoNoEliminable?.let { mensaje ->
        AvisoDialog(titulo = "No se puede eliminar", mensaje = mensaje, onDismiss = viewModel::limpiarAviso)
    }

    proveedorEliminando?.let { proveedor ->
        ConfirmDeleteDialog(
            itemLabel = proveedor.nombre,
            onConfirm = {
                viewModel.eliminar(proveedor)
                proveedorEliminando = null
            },
            onDismiss = { proveedorEliminando = null }
        )
    }
}

@Composable
private fun EditarProveedorDialog(
    proveedor: ProveedorEntity,
    viewModel: ProveedorViewModel,
    onDismiss: () -> Unit,
    onEliminar: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf(proveedor.nombre) }
    var comercial by rememberSaveable { mutableStateOf(proveedor.comercial.orEmpty()) }
    var telefono by rememberSaveable { mutableStateOf(proveedor.telefono.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(proveedor.email.orEmpty()) }
    var nifCif by rememberSaveable { mutableStateOf(proveedor.nifCif.orEmpty()) }
    var direccion by rememberSaveable { mutableStateOf(proveedor.direccion.orEmpty()) }
    var codigoPostal by rememberSaveable { mutableStateOf(proveedor.codigoPostal.orEmpty()) }
    var ciudad by rememberSaveable { mutableStateOf(proveedor.ciudad.orEmpty()) }
    var exentoIva by rememberSaveable { mutableStateOf(proveedor.exentoIva) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar proveedor") },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = "ID: ${proveedor.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LabeledTextField(
                    label = "Nombre",
                    value = nombre,
                    onValueChange = { nombre = it },
                    isError = viewModel.error != null,
                    supportingText = { viewModel.error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    LabeledTextField(
                        label = "NIF/CIF",
                        value = nifCif,
                        onValueChange = { nifCif = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LabeledTextField(
                        label = "Comercial",
                        value = comercial,
                        onValueChange = { comercial = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row {
                    LabeledTextField(
                        label = "Teléfono",
                        value = telefono,
                        onValueChange = { telefono = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LabeledTextField(
                        label = "Email",
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                LabeledTextField(
                    label = "Dirección",
                    value = direccion,
                    onValueChange = { direccion = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    LabeledTextField(
                        label = "C. Postal",
                        value = codigoPostal,
                        onValueChange = { codigoPostal = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LabeledTextField(
                        label = "Ciudad",
                        value = ciudad,
                        onValueChange = { ciudad = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exentoIva, onCheckedChange = { exentoIva = it })
                    Text("Exento de IVA y recargo (sellos)")
                }
                TextButton(onClick = onEliminar) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.actualizar(
                    proveedor, nombre, comercial, telefono, email,
                    nifCif, direccion, codigoPostal, ciudad, exentoIva
                ) { onDismiss() }
            }) { Text("Guardar cambios") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
