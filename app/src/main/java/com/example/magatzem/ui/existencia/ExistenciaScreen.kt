package com.example.magatzem.ui.existencia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.ui.common.ConfirmDeleteDialog
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.productos.ProductoForm
import com.example.magatzem.ui.productos.ProductoViewModel

/**
 * Listado de todos los artículos (ver [ProductoViewModel.productos]; sin distinguir Pre/Nuevo,
 * distinción descartada el 2026-09-30): "Añadir +" lleva a [ExistenciaAltaScreen]; tocar una fila
 * abre este mismo formulario para editarla, con Eliminar. (Antes se llamaba "Existentes";
 * renombrado a "Existencia" el 2026-09-30.)
 */
@Composable
fun ExistenciaScreen(viewModel: ProductoViewModel = viewModel(), onAnadir: () -> Unit = {}) {
    val productos by viewModel.productos.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val proveedores by viewModel.proveedores.collectAsState()
    var productoEditando by remember { mutableStateOf<ProductoEntity?>(null) }
    var productoEliminando by remember { mutableStateOf<ProductoEntity?>(null) }
    var textoBusqueda by rememberSaveable { mutableStateOf("") }

    val productosFiltrados = remember(productos, textoBusqueda) {
        if (textoBusqueda.isBlank()) {
            productos
        } else {
            productos.filter { producto ->
                producto.nombre.contains(textoBusqueda, ignoreCase = true) ||
                    producto.sku?.contains(textoBusqueda, ignoreCase = true) == true ||
                    producto.codigoBarras?.contains(textoBusqueda, ignoreCase = true) == true ||
                    producto.referenciaFabricante?.contains(textoBusqueda, ignoreCase = true) == true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Existencia", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledTextField(
                    label = "Buscar",
                    value = textoBusqueda,
                    onValueChange = { textoBusqueda = it },
                    singleLine = true,
                    compacto = true,
                    modifier = Modifier.width(220.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onAnadir) { Text("Añadir +") }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        if (productos.isEmpty()) {
            Text(
                text = "No hay artículos añadidos todavía.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (productosFiltrados.isEmpty()) {
            Text(
                text = "Ningún artículo coincide con \"$textoBusqueda\".",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(productosFiltrados, key = { it.id }) { producto ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { productoEditando = producto }
                            .padding(vertical = 10.dp)
                    ) {
                        // SKU y nombre ya no son obligatorios: sin SKU no se antepone nada (en vez
                        // de mostrar literalmente "null"), y sin nombre se avisa en vez de una fila
                        // en blanco imposible de identificar en la lista.
                        val nombreMostrado = producto.nombre.ifBlank { "(sin nombre)" }
                        Text(
                            text = producto.sku?.let { "$it · $nombreMostrado" } ?: nombreMostrado,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Coste: %.2f € · PVP: %.2f € · Existencia: %d".format(
                                producto.coste, producto.precioVenta, producto.existencia
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    productoEditando?.let { producto ->
        EditarExistenteDialog(
            producto = producto,
            categorias = categorias,
            proveedores = proveedores,
            viewModel = viewModel,
            onDismiss = {
                productoEditando = null
                viewModel.limpiarError()
            },
            onEliminar = {
                productoEditando = null
                productoEliminando = producto
            }
        )
    }

    viewModel.avisoEliminar?.let { aviso ->
        AlertDialog(
            onDismissRequest = viewModel::cerrarAvisoEliminar,
            title = { Text("No se puede borrar") },
            text = { Text(aviso) },
            confirmButton = { TextButton(onClick = viewModel::cerrarAvisoEliminar) { Text("Aceptar") } }
        )
    }

    productoEliminando?.let { producto ->
        ConfirmDeleteDialog(
            itemLabel = producto.nombre,
            onConfirm = {
                viewModel.eliminar(producto)
                productoEliminando = null
            },
            onDismiss = { productoEliminando = null }
        )
    }
}

@Composable
private fun EditarExistenteDialog(
    producto: ProductoEntity,
    categorias: List<CategoriaEntity>,
    proveedores: List<ProveedorEntity>,
    viewModel: ProductoViewModel,
    onDismiss: () -> Unit,
    onEliminar: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface {
            Card(modifier = Modifier.padding(24.dp)) {
                Column {
                    ProductoForm(
                        titulo = "Editar artículo",
                        inicial = producto,
                        categorias = categorias,
                        proveedores = proveedores,
                        error = viewModel.error,
                        onCancelar = onDismiss,
                        onEliminar = onEliminar
                    ) { sku, codigoBarras, referenciaFabricante, nombre, categoriaId, proveedorId, coste, margen, precioVenta, existencia, minimo, maximo, mostrarEnTeclado ->
                        viewModel.actualizar(
                            producto, sku, codigoBarras, referenciaFabricante, nombre, categoriaId, proveedorId,
                            coste, margen, precioVenta, existencia, minimo, maximo, mostrarEnTeclado
                        ) { onDismiss() }
                    }
                }
            }
        }
    }
}
