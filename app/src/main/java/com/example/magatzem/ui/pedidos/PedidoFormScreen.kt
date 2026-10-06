package com.example.magatzem.ui.pedidos

import com.example.magatzem.ui.common.formatearFecha
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.ui.common.SelectorDropdown
import com.example.magatzem.ui.common.mostrarTecladoEnPantalla

import com.example.magatzem.data.ESTADO_PEDIDO_PENDIENTE
import com.example.magatzem.ui.documentos.DatosDocumentoDialog
import com.example.magatzem.ui.documentos.TipoDoc
private val ColorBajoMinimo = Color(0xFFD32F2F) // rojo
private val ColorEnMinimo = Color(0xFF1565C0) // azul
private val ColorSobreMinimo = Color(0xFF2E7D32) // verde

/**
 * Pedidos a proveedor. Arriba: título, selector de proveedor en el centro, y a la derecha Nuevo y Volver. Primero se
 * elige proveedor; si tiene pedidos pendientes se listan y al pulsar uno se abre; Nuevo empieza otro. Dentro de un
 * pedido se ven primero los artículos que ya tenía (si se abrió uno) y debajo las categorías, colapsadas, para seguir
 * entrando. No hay botón de guardar: cada cambio se guarda al momento. `pedidoId` no nulo abre directamente ese pedido
 * (desde Oficina).
 */
@Composable
fun PedidoFormScreen(
    pedidoId: Long?,
    viewModel: PedidoViewModel = viewModel(),
    onGuardado: () -> Unit = {},
    onVolver: () -> Unit = {},
    onPasado: (TipoDoc, Long) -> Unit = { _, _ -> }
) {
    var pasarA by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pedidoId) {
        if (pedidoId != null) viewModel.abrirPedido(pedidoId) else viewModel.seleccionarProveedor(null)
    }

    val proveedores by viewModel.proveedores.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val productos by viewModel.productos.collectAsState()
    val todosPedidos by viewModel.pedidos.collectAsState()

    val proveedorId = viewModel.proveedorSeleccionadoId
    val enPedido = viewModel.enPedido
    val categoriaPorId = remember(categorias) { categorias.associateBy { it.id } }
    val pendientes = remember(todosPedidos, proveedorId) {
        todosPedidos.filter { it.proveedorId == proveedorId && it.estado == ESTADO_PEDIDO_PENDIENTE }
    }

    // Categorías desplegadas (colapsadas por defecto, para no saturar la pantalla de golpe).
    val categoriasAbiertas = remember { mutableStateMapOf<Long?, Boolean>() }

    val productosProveedor = remember(productos, proveedorId) {
        productos.filter { it.proveedorId == proveedorId }
    }

    // Rellena la sugerencia (máximo - existencia) para lo que aún no tenga un valor tecleado; no pisa ediciones.
    LaunchedEffect(productosProveedor, enPedido) {
        productosProveedor.forEach { producto ->
            if (producto.id !in viewModel.unidades) {
                viewModel.actualizarUnidades(producto.id, viewModel.sugerencia(producto))
            }
        }
    }

    // Los artículos que ya tenía el pedido al abrirlo van primero y aparte; el resto, en sus categorías.
    val iniciales = viewModel.inicialesIds
    val productosEnPedido = productosProveedor.filter { it.id in iniciales }
    val productosParaCategorias = productosProveedor.filter { it.id !in iniciales }

    // Agrupados por categoría (orden alfabético) y, dentro de cada una, de más por debajo del mínimo a
    // más por encima: es decir, ascendente por (existencia - mínimo).
    val grupos = remember(productosParaCategorias, categorias) {
        productosParaCategorias
            .groupBy { it.categoriaId }
            .toList()
            .sortedBy { (categoriaId, _) -> categoriaPorId[categoriaId]?.nombre ?: "" }
            .map { (categoriaId, lista) ->
                categoriaId to lista.sortedBy { it.existencia - it.minimo }
            }
    }

    // imePadding: con el teclado abierto la lista se encoge por encima de él y se puede desplazar hasta las últimas líneas.
    Column(modifier = Modifier.fillMaxSize().imePadding().padding(24.dp)) {
        // Primera línea: título a la izquierda, Nuevo y Volver a la derecha.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Pedidos", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = viewModel::nuevoPedido, enabled = proveedorId != null) { Text("Nuevo") }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = {
                // Dentro de un pedido abierto desde la lista, vuelve a la lista; si no, sale de la pantalla.
                if (enPedido && pedidoId == null) viewModel.salirDePedido() else onVolver()
            }) { Text("Volver") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Segunda línea: desplegable de proveedor y, mientras no haya uno elegido, el aviso.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SelectorDropdown(
                label = "Proveedor",
                opciones = proveedores.map { it.id to it.nombre },
                seleccionado = proveedorId,
                onSeleccionar = { viewModel.seleccionarProveedor(it) },
                modifier = Modifier.widthIn(max = 320.dp)
            )
            if (proveedorId == null) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "Selecciona proveedor", style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (enPedido && viewModel.seleccionados.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResumenPedidoFila(viewModel.resumen())
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { pasarA = TipoDoc.ALBARAN.name }) { Text("Pasar a albarán") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { pasarA = TipoDoc.FACTURA.name }) { Text("Pasar a factura") }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        when {
            proveedorId == null -> {}
            !enPedido -> {
                if (pendientes.isEmpty()) {
                    Text(
                        text = "Este proveedor no tiene pedidos pendientes. Pulsa Nuevo para empezar uno.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Pedidos pendientes (${pendientes.size}) — pulsa uno para abrirlo",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(pendientes, key = { it.id }) { pedido ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.abrirPedido(pedido.id) }.padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pedido del ${formatearFecha(pedido.fecha.take(10))}", style = MaterialTheme.typography.bodyLarge)
                                Text("%.2f €".format(pedido.total), style = MaterialTheme.typography.bodyLarge)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            productosProveedor.isEmpty() -> {
                Text(text = "Este proveedor no tiene artículos.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (productosEnPedido.isNotEmpty()) {
                        item(key = "cabecera-pedido") {
                            Text(
                                text = "En este pedido (${productosEnPedido.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            HorizontalDivider()
                        }
                        items(productosEnPedido, key = { "pedido-${it.id}" }) { producto ->
                            ProductoPedidoRow(
                                producto = producto,
                                unidades = viewModel.unidades[producto.id] ?: "",
                                onUnidadesChange = { viewModel.actualizarUnidades(producto.id, it) },
                                seleccionado = producto.id in viewModel.seleccionados,
                                onAgregar = { viewModel.agregar(producto.id) }
                            )
                            HorizontalDivider()
                        }
                    }
                    grupos.forEach { (categoriaId, lista) ->
                        val abierta = categoriasAbiertas[categoriaId] ?: false
                        item(key = "cabecera-$categoriaId") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { categoriasAbiertas[categoriaId] = !abierta }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (abierta) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                    contentDescription = if (abierta) "Contraer" else "Desplegar"
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${categoriaPorId[categoriaId]?.nombre ?: "Sin categoría"} (${lista.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            HorizontalDivider()
                        }
                        if (abierta) {
                            items(lista, key = { it.id }) { producto ->
                                ProductoPedidoRow(
                                    producto = producto,
                                    unidades = viewModel.unidades[producto.id] ?: "",
                                    onUnidadesChange = { viewModel.actualizarUnidades(producto.id, it) },
                                    seleccionado = producto.id in viewModel.seleccionados,
                                    onAgregar = { viewModel.agregar(producto.id) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    pasarA?.let { nombre ->
        val tipoPase = TipoDoc.valueOf(nombre)
        DatosDocumentoDialog(
            titulo = "Pasar a ${tipoPase.nombre.lowercase()}", numeroInicial = "", textoAceptar = "Pasar",
            onAceptar = { numero, fecha -> viewModel.pasarA(tipoPase, numero, fecha, null) { id -> pasarA = null; onPasado(tipoPase, id) } },
            onDismiss = { pasarA = null; viewModel.limpiarErrorPase() },
            error = viewModel.errorPase
        )
        viewModel.duplicadoPase?.let { (t, numero, fecha) ->
            AlertDialog(
                onDismissRequest = viewModel::cancelarDuplicadoPase,
                title = { Text("Ya existe ${t.nombre.lowercase()} $numero") },
                text = { Text("Este proveedor ya tiene uno con ese número. ¿Quieres añadir las líneas del pedido a él? Las cantidades de los artículos repetidos se sumarán.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.pasarA(t, numero, fecha, true) { id -> pasarA = null; onPasado(t, id) } }) { Text("Añadir") }
                },
                dismissButton = { TextButton(onClick = viewModel::cancelarDuplicadoPase) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
private fun ResumenPedidoFila(resumen: ResumenPedido) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ImporteResumen("Importe", resumen.importe)
        ImporteResumen("R.E. (5,2%)", resumen.importeRecargo)
        ImporteResumen("IVA (21%)", resumen.importeIva)
        ImporteResumen("Total", resumen.total, destacado = true)
    }
}

@Composable
private fun ImporteResumen(etiqueta: String, valor: Double, destacado: Boolean = false) {
    Column {
        Text(text = etiqueta, style = MaterialTheme.typography.labelSmall)
        Text(
            text = "%.2f €".format(valor),
            style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProductoPedidoRow(
    producto: ProductoEntity,
    unidades: String,
    onUnidadesChange: (String) -> Unit,
    seleccionado: Boolean,
    onAgregar: () -> Unit
) {
    val colorEstado = when {
        producto.existencia < producto.minimo -> ColorBajoMinimo
        producto.existencia == producto.minimo -> ColorEnMinimo
        else -> ColorSobreMinimo
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // El pedido se sirve por la referencia del proveedor (REF), no por el SKU propio.
            Text(
                text = "${producto.referenciaFabricante ?: "sin REF"} · ${producto.nombre} · Coste U. %.2f €".format(
                    producto.coste
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Mínimo %d · Máximo %s · Existencia %d".format(
                    producto.minimo, producto.maximo?.toString() ?: "—", producto.existencia
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.width(96.dp))
        OutlinedTextField(
            value = unidades,
            onValueChange = { onUnidadesChange(it.filter(Char::isDigit)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorEstado,
                unfocusedBorderColor = colorEstado,
                focusedTextColor = colorEstado,
                unfocusedTextColor = colorEstado,
                cursorColor = colorEstado
            ),
            modifier = Modifier.width(50.dp).height(52.dp).mostrarTecladoEnPantalla()
        )
        RadioButton(
            selected = seleccionado,
            onClick = onAgregar,
            colors = RadioButtonDefaults.colors(selectedColor = ColorSobreMinimo)
        )
    }
}
