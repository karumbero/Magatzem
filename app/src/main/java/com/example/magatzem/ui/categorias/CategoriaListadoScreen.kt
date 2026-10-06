package com.example.magatzem.ui.categorias

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.ui.common.AvisoDialog
import com.example.magatzem.ui.common.ConfirmDeleteDialog
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown

@Composable
fun CategoriaListadoScreen(viewModel: CategoriaViewModel = viewModel(), onAnadir: () -> Unit = {}) {
    val categorias by viewModel.categorias.collectAsState()
    val padres = remember(categorias) { categorias.filter { it.parentId == null }.sortedBy { it.nombre.lowercase() } }
    val hijasPorPadre = remember(categorias) {
        categorias.filter { it.parentId != null }.groupBy { it.parentId }
            .mapValues { (_, hijas) -> hijas.sortedBy { it.nombre.lowercase() } }
    }
    // Colapsado por defecto: el segundo nivel solo se ve al desplegar cada categoría padre.
    val expandido = remember { mutableStateMapOf<Long, Boolean>() }
    var categoriaEditando by remember { mutableStateOf<CategoriaEntity?>(null) }
    var categoriaEliminando by remember { mutableStateOf<CategoriaEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Categorías", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onAnadir) { Text("Añadir +") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            padres.forEach { padre ->
                val hijas = hijasPorPadre[padre.id].orEmpty()
                val abierta = expandido[padre.id] ?: false
                item(key = "padre-${padre.id}") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hijas.isNotEmpty()) {
                            Icon(
                                imageVector = if (abierta) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                contentDescription = if (abierta) "Contraer" else "Desplegar",
                                modifier = Modifier
                                    .clickable { expandido[padre.id] = !abierta }
                                    .padding(12.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                        Text(
                            text = padre.nombre,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { categoriaEditando = padre }
                                .padding(vertical = 12.dp)
                        )
                    }
                    HorizontalDivider()
                }
                if (abierta) {
                    items(hijas, key = { it.id }) { hija ->
                        Text(
                            text = "— ${hija.nombre}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoriaEditando = hija }
                                .padding(start = 48.dp, top = 12.dp, bottom = 12.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    categoriaEditando?.let { categoria ->
        EditarCategoriaDialog(
            categoria = categoria,
            todasLasCategorias = categorias,
            viewModel = viewModel,
            onDismiss = {
                categoriaEditando = null
                viewModel.limpiarError()
            },
            onEliminar = {
                categoriaEditando = null
                viewModel.comprobarEliminar(categoria) { categoriaEliminando = categoria }
            }
        )
    }

    viewModel.avisoNoEliminable?.let { mensaje ->
        AvisoDialog(titulo = "No se puede eliminar", mensaje = mensaje, onDismiss = viewModel::limpiarAviso)
    }

    categoriaEliminando?.let { categoria ->
        ConfirmDeleteDialog(
            itemLabel = categoria.nombre,
            onConfirm = {
                viewModel.eliminar(categoria)
                categoriaEliminando = null
            },
            onDismiss = { categoriaEliminando = null }
        )
    }
}

@Composable
private fun EditarCategoriaDialog(
    categoria: CategoriaEntity,
    todasLasCategorias: List<CategoriaEntity>,
    viewModel: CategoriaViewModel,
    onDismiss: () -> Unit,
    onEliminar: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf(categoria.nombre) }
    var categoriaPadreId by rememberSaveable { mutableStateOf(categoria.parentId) }

    // Solo categorías de primer nivel pueden ser "padre" (dos niveles nada más), y nunca ella misma.
    val opcionesPadre = remember(todasLasCategorias, categoria.id) {
        listOf<Pair<Long?, String>>(null to "Ninguna (primer nivel)") +
            todasLasCategorias
                .filter { it.parentId == null && it.id != categoria.id }
                .sortedBy { it.nombre.lowercase() }
                .map { it.id to it.nombre }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar categoría") },
        text = {
            Column {
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
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onEliminar) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.actualizar(categoria, nombre, categoriaPadreId) { onDismiss() }
            }) { Text("Guardar cambios") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
