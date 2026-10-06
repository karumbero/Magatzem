package com.example.magatzem.ui.proveedores

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.ui.common.LabeledTextField

@Composable
fun ProveedorAltaScreen(viewModel: ProveedorViewModel = viewModel(), onCancelar: () -> Unit = {}) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var comercial by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var nifCif by rememberSaveable { mutableStateOf("") }
    var direccion by rememberSaveable { mutableStateOf("") }
    var codigoPostal by rememberSaveable { mutableStateOf("") }
    var ciudad by rememberSaveable { mutableStateOf("") }
    var exentoIva by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.padding(24.dp).widthIn(max = 480.dp)) {
            Text(text = "Nuevo proveedor", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            LabeledTextField(
                label = "Nombre",
                value = nombre,
                onValueChange = { nombre = it },
                isError = viewModel.error != null,
                supportingText = { viewModel.error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
            LabeledTextField(
                label = "Dirección",
                value = direccion,
                onValueChange = { direccion = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                Text("Exento de IVA y recargo (p. ej. sellos de Correos)")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(onClick = {
                    viewModel.crear(nombre, comercial, telefono, email, nifCif, direccion, codigoPostal, ciudad, exentoIva) {
                        exentoIva = false
                        nombre = ""
                        comercial = ""
                        telefono = ""
                        email = ""
                        nifCif = ""
                        direccion = ""
                        codigoPostal = ""
                        ciudad = ""
                    }
                }) {
                    Text("Guardar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onCancelar) {
                    Text("Finalizar")
                }
            }
        }
    }
}
