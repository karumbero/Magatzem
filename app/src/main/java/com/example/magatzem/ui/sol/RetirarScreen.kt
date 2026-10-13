package com.example.magatzem.ui.sol

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.ui.common.LabeledTextField

internal fun euros(v: Double) = String.format(java.util.Locale.getDefault(), "%.2f €", v)

/**
 * Retirar: se busca un artículo (SKU, REF, EAN o nombre), se indica la cantidad y se añade a la lista con su PVP y el importe
 * (cantidad × PVP). Arriba, el total y el botón Finalizar, que guarda las líneas con la fecha.
 */
@Composable
fun RetirarScreen(viewModel: RetirarViewModel = viewModel()) {
    val productos by viewModel.productos.collectAsState()
    var busqueda by rememberSaveable { mutableStateOf("") }
    var cantidadTexto by rememberSaveable { mutableStateOf("1") }
    var pvpTexto by rememberSaveable { mutableStateOf("") }
    // Se guarda el id (no el artículo) para que sobreviva al girar la tablet.
    var seleccionadoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val seleccionado = productos.firstOrNull { it.id == seleccionadoId }
    val focoBusqueda = remember { FocusRequester() }
    val focoCantidad = remember { FocusRequester() }

    fun elegir(p: ProductoEntity) {
        seleccionadoId = p.id
        busqueda = ""
        cantidadTexto = "1"
        pvpTexto = String.format(java.util.Locale.ROOT, "%.2f", p.precioVenta)
    }

    fun anadir() {
        val p = seleccionado ?: return
        val pvp = pvpTexto.trim().replace(',', '.').toDoubleOrNull() ?: p.precioVenta
        viewModel.anadir(p, cantidadTexto.toIntOrNull() ?: 0, pvp)
        seleccionadoId = null
        cantidadTexto = "1"
        busqueda = ""
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Retirar", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("Total: ${euros(viewModel.total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = viewModel::finalizar, enabled = viewModel.lineas.isNotEmpty()) { Text("Finalizar") }
        }
        viewModel.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(modifier = Modifier.height(8.dp))

        val sel = seleccionado
        if (sel == null) {
            LabeledTextField(
                label = "Buscar artículo (SKU, REF, código de barras o nombre)",
                value = busqueda,
                onValueChange = { busqueda = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.exacto(busqueda, productos)?.let { elegir(it) } }),
                modifier = Modifier.fillMaxWidth(),
                campoModifier = Modifier.focusRequester(focoBusqueda)
            )
            viewModel.buscar(busqueda, productos).forEach { p ->
                Row(modifier = Modifier.fillMaxWidth().clickable { elegir(p) }.padding(vertical = 8.dp)) {
                    Text(p.sku ?: "—", fontWeight = FontWeight.Bold, modifier = Modifier.width(140.dp))
                    Text(p.nombre, modifier = Modifier.weight(1f))
                    Text(euros(p.precioVenta))
                }
                HorizontalDivider()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(listOfNotNull(sel.sku, sel.nombre).joinToString(" · "), fontWeight = FontWeight.Bold)
                    Text("Existencia: ${sel.existencia}")
                }
                LabeledTextField(
                    label = "PVP",
                    value = pvpTexto,
                    onValueChange = { pvpTexto = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(9) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.width(110.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LabeledTextField(
                    label = "Cantidad",
                    value = cantidadTexto,
                    onValueChange = { cantidadTexto = it.filter(Char::isDigit).take(5) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { anadir() }),
                    modifier = Modifier.width(120.dp),
                    campoModifier = Modifier.focusRequester(focoCantidad)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = ::anadir) { Text("Añadir") }
                TextButton(onClick = { seleccionadoId = null }) { Text("Cancelar") }
            }
        }
        // El foco va al campo que toca: tras añadir, de nuevo a la búsqueda (para escanear el siguiente).
        androidx.compose.runtime.LaunchedEffect(seleccionado) {
            runCatching { if (seleccionado == null) focoBusqueda.requestFocus() else focoCantidad.requestFocus() }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("SKU", fontWeight = FontWeight.Bold, modifier = Modifier.width(140.dp))
            Text("Nombre", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("PVP", fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
            Text("Cant.", fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
            Text("Importe", fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
            Spacer(modifier = Modifier.width(48.dp))
        }
        HorizontalDivider()
        if (viewModel.lineas.isEmpty()) {
            Text("Todavía no hay artículos en esta retirada.", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(viewModel.lineas) { i, l ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(l.producto.sku ?: "—", modifier = Modifier.width(140.dp))
                        Text(l.producto.nombre, modifier = Modifier.weight(1f))
                        // El PVP y la cantidad se pueden corregir en la propia línea.
                        var pvpLinea by rememberSaveable(i, l.producto.id) { mutableStateOf(String.format(java.util.Locale.ROOT, "%.2f", l.pvp)) }
                        androidx.compose.foundation.text.BasicTextField(
                            value = pvpLinea,
                            onValueChange = {
                                pvpLinea = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(9)
                                pvpLinea.replace(',', '.').toDoubleOrNull()?.let { v -> viewModel.cambiarPvp(i, v) }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.width(80.dp).padding(end = 10.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)).padding(6.dp)
                        )
                        var cantLinea by rememberSaveable(i, l.producto.id) { mutableStateOf("${l.cantidad}") }
                        androidx.compose.foundation.text.BasicTextField(
                            value = cantLinea,
                            onValueChange = {
                                cantLinea = it.filter(Char::isDigit).take(5)
                                cantLinea.toIntOrNull()?.let { v -> viewModel.cambiarCantidad(i, v) }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.width(50.dp).padding(end = 10.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)).padding(6.dp)
                        )
                        Text(euros(l.importe), fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                        TextButton(onClick = { viewModel.quitar(i) }, modifier = Modifier.width(48.dp)) { Text("✕") }
                    }
                    HorizontalDivider()
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Text("Total: ${euros(viewModel.total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
