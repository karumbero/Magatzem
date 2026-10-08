package com.example.magatzem.ui.incidencias

import com.example.magatzem.ui.common.contieneBusqueda
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.IncidenciaEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.TIPO_INCIDENCIA_FALTA_ENTRADA
import com.example.magatzem.data.TIPO_INCIDENCIA_ROBO
import com.example.magatzem.data.TIPO_INCIDENCIA_ROTURA
import com.example.magatzem.data.nombreTipoIncidencia
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown
import com.example.magatzem.ui.common.formatearFecha

private val TIPOS = listOf(TIPO_INCIDENCIA_ROBO, TIPO_INCIDENCIA_ROTURA, TIPO_INCIDENCIA_FALTA_ENTRADA)

/**
 * Movimientos → Incidencias: los artículos de baja (dañados o perdidos). Pulsar una fila abre su
 * formulario, con Guardar y Recuperar (devuelve las unidades a la existencia y borra la incidencia).
 */
@Composable
fun IncidenciasScreen(viewModel: IncidenciaViewModel = viewModel()) {
    val incidencias by viewModel.incidencias.collectAsState()
    val productos by viewModel.productos.collectAsState()
    var anadiendo by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<IncidenciaEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Incidencias", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { anadiendo = true }) { Text("Añadir +") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        if (incidencias.isEmpty()) {
            Text("No hay incidencias.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Pérdida: %.2f €".format(incidencias.sumOf { it.cantidad * it.costeUnitario }),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(incidencias, key = { it.id }) { inc ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { editando = inc }.padding(vertical = 12.dp)
                    ) {
                        Text(
                            "${inc.cantidad} × ${listOfNotNull(inc.productoSku, inc.productoNombre).joinToString(" · ")}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            buildString {
                                append(nombreTipoIncidencia(inc.tipo)).append(" · ").append(formatearFecha(inc.fecha))
                                append(" · %.2f €".format(inc.cantidad * inc.costeUnitario))
                                inc.nota?.let { append(" · ").append(it) }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (anadiendo) {
        IncidenciaDialog(
            titulo = "Añadir incidencia",
            productos = productos,
            inicial = null,
            onConfirm = { producto, tipo, cantidad, nota ->
                viewModel.anadir(producto!!, tipo, cantidad, nota)
                anadiendo = false
            },
            onRecuperar = null,
            onDismiss = { anadiendo = false }
        )
    }
    editando?.let { inc ->
        IncidenciaDialog(
            titulo = "Incidencia",
            productos = productos,
            inicial = inc,
            onConfirm = { _, tipo, cantidad, nota ->
                viewModel.editar(inc, tipo, cantidad, nota)
                editando = null
            },
            onRecuperar = {
                viewModel.recuperar(inc)
                editando = null
            },
            onDismiss = { editando = null }
        )
    }
}

/** Alta (elegir artículo buscando) o edición (artículo fijo) de una incidencia. */
@Composable
private fun IncidenciaDialog(
    titulo: String,
    productos: List<ProductoEntity>,
    inicial: IncidenciaEntity?,
    onConfirm: (ProductoEntity?, tipo: String, cantidad: Int, nota: String) -> Unit,
    onRecuperar: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var producto by remember { mutableStateOf<ProductoEntity?>(null) }
    var busqueda by remember { mutableStateOf("") }
    var tipoIdx by remember { mutableStateOf(TIPOS.indexOf(inicial?.tipo).coerceAtLeast(0)) }
    var cantidad by remember { mutableStateOf(inicial?.cantidad?.toString() ?: "1") }
    var nota by remember { mutableStateOf(inicial?.nota ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmandoRecuperar by remember { mutableStateOf(false) }
    val coincidencias = remember(busqueda, productos) {
        val q = busqueda.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else productos.filter { p ->
            contieneBusqueda(q, p.sku, p.codigoBarras, p.referenciaFabricante, p.nombre)
        }.take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inicial != null) {
                    Text(
                        listOfNotNull(inicial.productoSku, inicial.productoNombre).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else if (producto != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${listOfNotNull(producto!!.sku, producto!!.nombre).joinToString(" · ")} (existencia ${producto!!.existencia})",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { producto = null }) { Text("Cambiar") }
                    }
                } else {
                    LabeledTextField(
                        label = "Buscar artículo (SKU, REF, código de barras o nombre)",
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        singleLine = true
                    )
                    coincidencias.forEach { p ->
                        Text(
                            "${listOfNotNull(p.sku, p.nombre).joinToString(" · ")} (existencia ${p.existencia})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { producto = p }.padding(vertical = 6.dp)
                        )
                    }
                }
                SelectorDropdown(
                    label = "Tipo",
                    opciones = TIPOS.mapIndexed { i, t -> i.toLong() as Long? to nombreTipoIncidencia(t) },
                    seleccionado = tipoIdx.toLong(),
                    onSeleccionar = { tipoIdx = (it ?: 0L).toInt() }
                )
                LabeledTextField(
                    label = "Unidades",
                    value = cantidad,
                    onValueChange = { cantidad = it.filter(Char::isDigit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                LabeledTextField(label = "Nota (opcional)", value = nota, onValueChange = { nota = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (onRecuperar != null) {
                    OutlinedButton(onClick = { confirmandoRecuperar = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Recuperar: devolver ${inicial?.cantidad ?: 0} a la existencia")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val unidades = cantidad.toIntOrNull()
                when {
                    inicial == null && producto == null -> error = "Elige el artículo"
                    unidades == null || unidades <= 0 -> error = "Las unidades deben ser mayores que 0"
                    else -> onConfirm(producto, TIPOS[tipoIdx], unidades, nota)
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (confirmandoRecuperar && onRecuperar != null) {
        AlertDialog(
            onDismissRequest = { confirmandoRecuperar = false },
            title = { Text("Recuperar") },
            text = { Text("¿Recuperar ${inicial?.cantidad} × ${inicial?.productoNombre}? Se devolverán a la existencia y se borrará la incidencia.") },
            confirmButton = { TextButton(onClick = { confirmandoRecuperar = false; onRecuperar() }) { Text("Recuperar") } },
            dismissButton = { TextButton(onClick = { confirmandoRecuperar = false }) { Text("Cancelar") } }
        )
    }
}
