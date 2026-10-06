package com.example.magatzem.ui.usuarios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.PIN_POR_DEFECTO
import com.example.magatzem.ui.common.LabeledTextField

@Composable
fun UsuarioAltaScreen(viewModel: UsuarioViewModel = viewModel(), onCancelar: () -> Unit = {}) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var nivel by rememberSaveable { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.padding(24.dp).widthIn(max = 480.dp)) {
            Text(text = "Nuevo usuario", style = MaterialTheme.typography.headlineSmall)
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
            LabeledTextField(
                label = "Nivel (1 dígito)",
                value = nivel,
                onValueChange = { if (it.length <= 1) nivel = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "El PIN inicial será $PIN_POR_DEFECTO. Se le pedirá cambiarlo la primera vez que inicie sesión.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(onClick = {
                    viewModel.crear(nombre, nivel) {
                        nombre = ""
                        nivel = ""
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
