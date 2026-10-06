package com.example.magatzem.ui.existencia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.ui.productos.ProductoForm
import com.example.magatzem.ui.productos.ProductoViewModel

/**
 * Dar de alta un artículo (uno a uno; "múltiple" queda para más adelante). Ver [ExistenciaScreen].
 * Sirve tanto para artículos que ya existían físicamente (coste a veces desconocido) como para
 * altas con coste de entrada conocido: ya no se distingue Pre/Nuevo (descartado el 2026-09-30).
 */
@Composable
fun ExistenciaAltaScreen(viewModel: ProductoViewModel = viewModel(), onCancelar: () -> Unit = {}) {
    val categorias by viewModel.categorias.collectAsState()
    val proveedores by viewModel.proveedores.collectAsState()
    // Cambia tras cada alta para reiniciar el formulario en blanco y poder seguir añadiendo seguido.
    var version by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        key(version) {
            ProductoForm(
                titulo = "Nuevo artículo",
                inicial = null,
                categorias = categorias,
                proveedores = proveedores,
                error = viewModel.error,
                onCancelar = onCancelar,
                onComprobarDuplicado = { sku, codigoBarras, referenciaFabricante ->
                    viewModel.comprobarDuplicadoTemprano(sku, codigoBarras, referenciaFabricante)
                }
            ) { sku, codigoBarras, referenciaFabricante, nombre, categoriaId, proveedorId, coste, margen, precioVenta, existencia, minimo, maximo, mostrarEnTeclado ->
                viewModel.crear(
                    sku, codigoBarras, referenciaFabricante, nombre, categoriaId, proveedorId,
                    coste, margen, precioVenta, existencia, minimo, maximo, mostrarEnTeclado
                ) {
                    version++
                }
            }
        }
    }

    viewModel.productoExistente?.let { producto ->
        ProductoExistenteDialog(
            producto = producto,
            onCerrar = {
                viewModel.limpiarProductoExistente()
                version++
            }
        )
    }
}

@Composable
private fun ProductoExistenteDialog(producto: ProductoEntity, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Artículo existente") },
        text = {
            Column {
                Text("SKU: ${producto.sku}")
                Text("REF: ${producto.referenciaFabricante ?: "—"}")
                Text("Código: ${producto.codigoBarras ?: "—"}")
                Text("Nombre: ${producto.nombre}")
            }
        },
        confirmButton = {
            TextButton(onClick = onCerrar) { Text("Cerrar") }
        }
    )
}
