package com.example.magatzem.ui.inventario

import com.example.magatzem.ui.common.contieneBusqueda
import com.example.magatzem.data.factorCoste
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.PaddingValues
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.INVENTARIO_ABIERTO
import com.example.magatzem.data.InventarioLineaEntity
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown
import com.example.magatzem.ui.common.formatearFecha
import com.example.magatzem.ui.common.hoyFecha
import com.example.magatzem.ui.common.mostrarTecladoEnPantalla
import kotlinx.coroutines.launch

/** Listado de inventarios: Nuevo crea uno con todos los artículos; pulsar uno lo abre para seguir contando. */
@Composable
fun InventarioListaScreen(onAbrir: (Long) -> Unit, viewModel: InventarioListaViewModel = viewModel()) {
    val inventarios by viewModel.inventarios.collectAsState()
    var nuevo by remember { mutableStateOf(false) }
    var nombre by rememberSaveable { mutableStateOf("") }
    var borrando by remember { mutableStateOf<com.example.magatzem.data.InventarioEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Inventario", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = { nombre = "Inventario ${hoyFecha()}"; nuevo = true }) { Text("Nuevo") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        if (inventarios.isEmpty()) {
            Text("Todavía no hay ningún inventario. Pulsa Nuevo: se crea con todos los artículos y su existencia actual, y se puede dejar a medias y retomar cuando quieras.")
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(inventarios, key = { it.inventario.id }) { r ->
                val i = r.inventario
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onAbrir(i.id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(i.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Creado el ${formatearFecha(i.fecha.take(10))} · " + if (i.estado == INVENTARIO_ABIERTO) "abierto" else "cerrado el ${formatearFecha((i.fechaCierre ?: "").take(10))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text("${r.contados} de ${r.total} contados", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { borrando = i }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider()
            }
        }
    }

    if (nuevo) {
        AlertDialog(
            onDismissRequest = { nuevo = false },
            title = { Text("Nuevo inventario") },
            text = { LabeledTextField(label = "Nombre", value = nombre, onValueChange = { nombre = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { nuevo = false; viewModel.nuevo(nombre) { onAbrir(it) } }) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { nuevo = false }) { Text("Cancelar") } }
        )
    }
    borrando?.let { i ->
        AlertDialog(
            onDismissRequest = { borrando = null },
            title = { Text("Borrar inventario") },
            text = { Text("¿Borrar \"${i.nombre}\" con todo lo contado? No se puede deshacer. No cambia las existencias de los artículos.") },
            confirmButton = { TextButton(onClick = { viewModel.borrar(i); borrando = null }) { Text("Borrar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { borrando = null }) { Text("Cancelar") } }
        )
    }
}

private enum class VistaConteo(val titulo: String) {
    TODOS("Todos"), PENDIENTE("Pendiente"), CONTADO("Contado"), CON_DIFERENCIA("Con diferencia")
}

/**
 * Un inventario abierto: una fila por artículo con su existencia, las bajas por incidencias, un campo para las unidades contadas y la
 * diferencia. Todo se guarda al momento, así que se puede cerrar y retomar. El lector (o SKU/REF + Intro) suma 1 unidad al artículo.
 */
@Composable
fun InventarioScreen(inventarioId: Long, onVolver: () -> Unit, viewModel: InventarioViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inventario by remember(inventarioId) { viewModel.inventario(inventarioId) }.collectAsState()
    val lineasBd by remember(inventarioId) { viewModel.lineas(inventarioId) }.collectAsState()
    // Los artículos sin existencia ni bajas no se listan (salvo que se les haya contado algo) hasta que se pulsa el botón de mostrarlos.
    var verSinExistencia by rememberSaveable { mutableStateOf(false) }
    val relevantes = remember(lineasBd) { lineasBd.filter { it.existenciaInicial != 0 || it.bajas > 0 || it.contadas != null } }
    val lineas = if (verSinExistencia) lineasBd else relevantes
    val productos by viewModel.productos.collectAsState()
    val actual = remember(productos) { productos.associate { it.id to it.existencia } }
    val proveedoresDb by viewModel.proveedores.collectAsState()
    val exentos = remember(proveedoresDb) { proveedoresDb.filter { it.exentoIva }.map { it.id }.toSet() }

    var vista by rememberSaveable { mutableStateOf(VistaConteo.TODOS.name) }
    var categoria by rememberSaveable { mutableStateOf<String?>(null) }
    var proveedor by rememberSaveable { mutableStateOf<String?>(null) }
    var texto by rememberSaveable { mutableStateOf("") }
    var confirmarCierre by remember { mutableStateOf(false) }
    var exportado by remember { mutableStateOf<java.io.File?>(null) }

    val abierto = inventario?.estado == INVENTARIO_ABIERTO
    fun diferencia(l: InventarioLineaEntity): Int? = l.contadas?.let { it - (l.existenciaAlContar ?: actual[l.productoId] ?: l.existenciaInicial) }
    val categorias = remember(lineas) { lineas.mapNotNull { it.categoria }.distinct().sorted() }
    val proveedores = remember(lineas) { lineas.mapNotNull { it.proveedor }.distinct().sorted() }
    val contados = lineas.count { it.contadas != null }
    val conDiferencia = lineas.count { (diferencia(it) ?: 0) != 0 }
    val filtro = texto.trim().lowercase()
    val visibles = remember(lineas, vista, categoria, proveedor, filtro, actual) {
        lineas.filter { l ->
            (categoria == null || l.categoria == categoria) && (proveedor == null || l.proveedor == proveedor) &&
                contieneBusqueda(filtro, l.sku, l.referencia, l.codigoBarras, l.nombre) &&
                when (VistaConteo.valueOf(vista)) {
                    VistaConteo.TODOS -> true
                    VistaConteo.PENDIENTE -> l.contadas == null
                    VistaConteo.CONTADO -> l.contadas != null
                    VistaConteo.CON_DIFERENCIA -> (diferencia(l) ?: 0) != 0
                }
        }
    }

    // Todo en una sola lista que se desplaza: cabecera, filtros y resumen suben con ella, para que haya sitio para miles de artículos.
    LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(24.dp)) {
        item(key = "cabecera") {
            Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(inventario?.nombre ?: "Inventario", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "$contados de ${lineas.size} contados · $conDiferencia con diferencia" + if (abierto) "" else " · CERRADO",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            OutlinedButton(onClick = {
                inventario?.let { i -> scope.launch { exportado = viewModel.exportarCsv(context, i) ?: return@launch } }
            }) { Text("Exportar CSV") }
            Spacer(modifier = Modifier.width(8.dp))
            inventario?.let { i ->
                if (abierto) Button(onClick = { confirmarCierre = true }) { Text("Cerrar inventario") }
                else Button(onClick = { viewModel.reabrir(i) }) { Text("Reabrir") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Estado del recuento: Todos / Pendiente / Contado / Con diferencia (con cuántos hay en cada uno).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VistaConteo.entries.forEach { v ->
                val n = when (v) {
                    VistaConteo.TODOS -> lineas.size
                    VistaConteo.PENDIENTE -> lineas.size - contados
                    VistaConteo.CONTADO -> contados
                    VistaConteo.CON_DIFERENCIA -> conDiferencia
                }
                FilterChip(selected = vista == v.name, onClick = { vista = v.name }, label = { Text("${v.titulo} ($n)") })
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Artículos con existencia 0 y sin bajas: ocultos por defecto.
            FilterChip(
                selected = verSinExistencia, onClick = { verSinExistencia = !verSinExistencia },
                label = { Text("Sin existencia ni bajas (${lineasBd.size - relevantes.size})") }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Categoría y proveedor se pueden combinar entre sí y con el estado.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            SelectorDropdown(
                label = "Categoría",
                opciones = listOf<Pair<Long?, String>>(null to "Todas") + categorias.mapIndexed { i, c -> i.toLong() as Long? to c },
                seleccionado = categoria?.let { categorias.indexOf(it).toLong() },
                onSeleccionar = { categoria = it?.let { i -> categorias.getOrNull(i.toInt()) } },
                modifier = Modifier.weight(1f)
            )
            SelectorDropdown(
                label = "Proveedor",
                opciones = listOf<Pair<Long?, String>>(null to "Todos") + proveedores.mapIndexed { i, c -> i.toLong() as Long? to c },
                seleccionado = proveedor?.let { proveedores.indexOf(it).toLong() },
                onSeleccionar = { proveedor = it?.let { i -> proveedores.getOrNull(i.toInt()) } },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it.replace("\n", "") },
                label = { Text("Buscar") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (abierto && viewModel.sumarUno(lineas, texto)) texto = ""
                }),
                modifier = Modifier.weight(1.4f).mostrarTecladoEnPantalla()
            )
        }
        viewModel.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(modifier = Modifier.height(8.dp))
            }
        }
        item(key = "resumen") {
            // Resumen valorado de TODOS los artículos del inventario (no cambia con los filtros ni con la búsqueda): coste = base + IVA + recargo de equivalencia (como en las ventas).
            val resumen = remember(lineas, actual, productos, exentos) {
                val porId = productos.associateBy { it.id }
                var uE = 0; var cE = 0.0; var pE = 0.0; var uC = 0; var cC = 0.0; var pC = 0.0; var uD = 0; var cD = 0.0
                lineas.forEach { l ->
                    val prod = porId[l.productoId] ?: return@forEach
                    val coste = prod.coste * factorCoste(prod.proveedorId in exentos)
                    val ex = actual[l.productoId] ?: l.existenciaInicial
                    uE += ex; cE += ex * coste; pE += ex * prod.precioVenta
                    l.contadas?.let { c ->
                        uC += c; cC += c * coste; pC += c * prod.precioVenta
                        val d = c - (l.existenciaAlContar ?: ex)
                        uD += d; cD += d * coste
                    }
                }
                listOf(uE to (cE to pE), uC to (cC to pC), uD to (cD to 0.0))
            }
            fun eur(v: Double) = "%,.2f €".format(v)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Resumen del inventario (${lineas.size} artículos)", style = MaterialTheme.typography.titleSmall)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("", modifier = Modifier.weight(1.2f)); Text("Unidades", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text("Coste (base+IVA+R.E.)", modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.labelMedium)
                        Text("Total PVP", modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium)
                    }
                    listOf("Existencia" to 0, "Contado" to 1).forEach { (nombre, i) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(nombre, modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                            Text("${resumen[i].first}", modifier = Modifier.weight(1f))
                            Text(eur(resumen[i].second.first), modifier = Modifier.weight(1.6f))
                            Text(eur(resumen[i].second.second), modifier = Modifier.weight(1.3f))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Diferencia", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                        Text((if (resumen[2].first > 0) "+" else "") + "${resumen[2].first}", modifier = Modifier.weight(1f))
                        Text((if (resumen[2].second.first > 0) "+" else "") + eur(resumen[2].second.first), modifier = Modifier.weight(1.6f))
                        Text("", modifier = Modifier.weight(1.3f))
                    }
                }
            }
        }
        item(key = "tabla") {
            Column {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("Artículo", modifier = Modifier.weight(3f), style = MaterialTheme.typography.labelLarge)
            Text("Existencia", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("Bajas", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
            Text("Contadas", modifier = Modifier.weight(1.1f), style = MaterialTheme.typography.labelLarge)
            Text("Diferencia", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider()
            }
        }
            items(visibles, key = { it.id }) { l ->
                val dif = diferencia(l)
                var campo by remember(l.id, l.contadas) { mutableStateOf(l.contadas?.toString() ?: "") }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(3f)) {
                        Text(listOfNotNull(l.sku, l.nombre).joinToString(" · "), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(listOfNotNull(l.categoria, l.proveedor).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${actual[l.productoId] ?: l.existenciaInicial}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${l.bajas}", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = campo,
                        onValueChange = { v ->
                            val limpio = v.filter(Char::isDigit).take(5)
                            campo = limpio
                            if (abierto) viewModel.contar(l, limpio.toIntOrNull())
                        },
                        enabled = abierto,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.1f).padding(end = 8.dp).mostrarTecladoEnPantalla()
                    )
                    Text(
                        dif?.let { if (it > 0) "+$it" else "$it" } ?: "—",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            dif == null -> Color.Gray
                            dif == 0 -> Color(0xFF2E7D32)
                            else -> Color(0xFFC62828)
                        }
                    )
                }
                HorizontalDivider()
            }
    }

    if (confirmarCierre) {
        AlertDialog(
            onDismissRequest = { confirmarCierre = false },
            title = { Text("Cerrar inventario") },
            text = { Text("Todo lo contado ya está guardado. Quedan ${lineas.size - contados} artículos sin contar. Al cerrarlo vuelves al listado y ya no se pueden cambiar los recuentos (se puede reabrir desde aquí). No cambia las existencias de los artículos.") },
            confirmButton = { TextButton(onClick = { inventario?.let(viewModel::cerrar); confirmarCierre = false; onVolver() }) { Text("Cerrar") } },
            dismissButton = { TextButton(onClick = { confirmarCierre = false }) { Text("Cancelar") } }
        )
    }
    exportado?.let { archivo ->
        AlertDialog(
            onDismissRequest = { exportado = null },
            title = { Text("CSV creado") },
            text = { Text("${archivo.name}\nSe puede enviar (correo, Drive…) o abrir con una hoja de cálculo; separado por punto y coma.") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, archivo.name)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Enviar inventario"))
                    exportado = null
                }) { Text("Enviar") }
            },
            dismissButton = { TextButton(onClick = { exportado = null }) { Text("Cerrar") } }
        )
    }
}
