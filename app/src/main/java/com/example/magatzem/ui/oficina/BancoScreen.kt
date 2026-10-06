package com.example.magatzem.ui.oficina

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.FormaPagoEntity
import com.example.magatzem.ui.common.LabeledTextField

/**
 * IBAN y titular de las cuentas bancarias del catálogo Formas de pago (Contado y Transferencia no
 * aparecen aquí, no son un banco). Para añadir un banco nuevo hay que hacerlo desde Ajustes→Formas
 * de pago; aquí solo se completan sus datos.
 */
@Composable
fun BancoScreen(viewModel: BancoViewModel = viewModel()) {
    val bancos by viewModel.bancos.collectAsState()
    var bancoEditando by remember { mutableStateOf<FormaPagoEntity?>(null) }
    var anadiendo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Bancos", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { viewModel.limpiarError(); anadiendo = true }) { Text("Añadir +") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (bancos.isEmpty()) {
            Text(
                text = "No hay ningún banco todavía. Pulsa Añadir +.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(bancos, key = { it.id }) { banco ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { bancoEditando = banco }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = banco.nombre + if (banco.principal) "  ·  principal (tarjeta e ingresos de caja)" else "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "IBAN: ${banco.iban?.ifBlank { "—" } ?: "—"}    " +
                            "Titular: ${banco.titular?.ifBlank { "—" } ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }
        }
    }

    if (anadiendo) {
        NuevoBancoDialog(
            error = viewModel.error,
            onGuardar = { nombre, iban, titular -> viewModel.crear(nombre, iban, titular) { anadiendo = false } },
            onDismiss = { anadiendo = false }
        )
    }

    bancoEditando?.let { banco ->
        EditarBancoDialog(
            banco = banco,
            onGuardar = { iban, titular ->
                viewModel.actualizar(banco, iban, titular)
                bancoEditando = null
            },
            onMarcarPrincipal = { viewModel.marcarPrincipal(banco); bancoEditando = null },
            onDismiss = { bancoEditando = null }
        )
    }
}

@Composable
private fun EditarBancoDialog(
    banco: FormaPagoEntity,
    onGuardar: (iban: String, titular: String) -> Unit,
    onMarcarPrincipal: () -> Unit,
    onDismiss: () -> Unit
) {
    var iban by rememberSaveable { mutableStateOf(banco.iban.orEmpty()) }
    var titular by rememberSaveable { mutableStateOf(banco.titular.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(banco.nombre) },
        text = {
            Column {
                LabeledTextField(
                    label = "IBAN",
                    value = iban,
                    onValueChange = { iban = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LabeledTextField(
                    label = "Titular",
                    value = titular,
                    onValueChange = { titular = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!banco.principal) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onMarcarPrincipal) { Text("Usar como banco principal") }
                    Text(
                        "Recibe los cobros con tarjeta y los ingresos de caja de los cierres de MiTPV. " +
                            "Con un solo banco no hace falta marcarlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(iban, titular) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun NuevoBancoDialog(
    error: String?,
    onGuardar: (nombre: String, iban: String, titular: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var iban by rememberSaveable { mutableStateOf("") }
    var titular by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir banco") },
        text = {
            Column {
                LabeledTextField(
                    label = "Nombre",
                    value = nombre,
                    onValueChange = { nombre = it },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LabeledTextField(label = "IBAN", value = iban, onValueChange = { iban = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                LabeledTextField(label = "Titular", value = titular, onValueChange = { titular = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onGuardar(nombre, iban, titular) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
