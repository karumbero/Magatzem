package com.example.magatzem.ui.categorias

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown

@Composable
fun CategoriaAltaScreen(viewModel: CategoriaViewModel = viewModel(), onCancelar: () -> Unit = {}) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var categoriaPadreId by rememberSaveable { mutableStateOf<Long?>(null) }
    val categorias by viewModel.categorias.collectAsState()

    // Solo categorías de primer nivel pueden ser "padre": dos niveles nada más.
    val opcionesPadre = remember(categorias) {
        listOf<Pair<Long?, String>>(null to "Ninguna (primer nivel)") +
            categorias.filter { it.parentId == null }.sortedBy { it.nombre.lowercase() }.map { it.id to it.nombre }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.padding(24.dp).widthIn(max = 480.dp)) {
            Text(text = "Nueva categoría", style = MaterialTheme.typography.headlineSmall)
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
            SelectorDropdown(
                label = "Categoría padre",
                opciones = opcionesPadre,
                seleccionado = categoriaPadreId,
                onSeleccionar = { categoriaPadreId = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(onClick = {
                    viewModel.crear(nombre, categoriaPadreId) { nombre = "" }
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
