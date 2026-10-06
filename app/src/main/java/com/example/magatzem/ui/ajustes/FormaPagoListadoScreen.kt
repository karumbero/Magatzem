package com.example.magatzem.ui.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.FormaPagoEntity
import com.example.magatzem.ui.common.LabeledTextField

@Composable
fun FormaPagoListadoScreen(viewModel: FormaPagoViewModel = viewModel()) {
    val formasPago by viewModel.formasPago.collectAsState()
    var anadiendo by rememberSaveable { mutableStateOf(false) }
    var formaPagoEditando by remember { mutableStateOf<FormaPagoEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Formas de pago", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { anadiendo = true }) { Text("Añadir +") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(formasPago, key = { it.id }) { formaPago ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { formaPagoEditando = formaPago }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = formaPago.nombre, style = MaterialTheme.typography.bodyLarge)
                    if (!formaPago.activo) {
                        Text(
                            text = "Inactiva",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (anadiendo) {
        FormaPagoDialog(
            titulo = "Añadir forma de pago",
            nombreInicial = "",
            activoInicial = true,
            mostrarActivo = false,
            error = viewModel.error,
            onDismiss = {
                anadiendo = false
                viewModel.limpiarError()
            },
            onGuardar = { nombre, _ -> viewModel.crear(nombre) { anadiendo = false } }
        )
    }

    formaPagoEditando?.let { formaPago ->
        FormaPagoDialog(
            titulo = "Editar forma de pago",
            nombreInicial = formaPago.nombre,
            activoInicial = formaPago.activo,
            mostrarActivo = true,
            error = viewModel.error,
            onDismiss = {
                formaPagoEditando = null
                viewModel.limpiarError()
            },
            onGuardar = { nombre, activo ->
                viewModel.actualizar(formaPago, nombre, activo) { formaPagoEditando = null }
            }
        )
    }
}

@Composable
private fun FormaPagoDialog(
    titulo: String,
    nombreInicial: String,
    activoInicial: Boolean,
    mostrarActivo: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onGuardar: (nombre: String, activo: Boolean) -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf(nombreInicial) }
    var activo by rememberSaveable { mutableStateOf(activoInicial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column {
                LabeledTextField(
                    label = "Nombre",
                    value = nombre,
                    onValueChange = { nombre = it },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                if (mostrarActivo) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Activa (se ofrece al pagar)")
                        Switch(checked = activo, onCheckedChange = { activo = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(nombre, activo) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
