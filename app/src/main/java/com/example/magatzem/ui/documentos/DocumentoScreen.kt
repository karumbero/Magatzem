package com.example.magatzem.ui.documentos

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown
import com.example.magatzem.ui.common.esFechaValida
import com.example.magatzem.ui.common.fechaDocumento
import com.example.magatzem.ui.common.hoyFecha
import com.example.magatzem.ui.common.mostrarTecladoEnPantalla
import com.example.magatzem.ui.common.pareceCodigoBarras
import com.example.magatzem.ui.productos.ProductoForm

/**
 * Albarán o Factura de Movimientos. Arriba: el nombre del documento con sus importes, el selector de proveedor en el centro
 * y Nuevo / Volver a la derecha. Primero se elige proveedor: se listan sus documentos pendientes (al pulsar uno se abre) y
 * Nuevo crea otro pidiendo número y fecha. Dentro, cada cambio se guarda al momento: no hay botón de guardar.
 * `docId` no nulo abre directamente ese documento (al pasar de pedido/albarán). Un albarán se puede pasar a factura.
 */
@Composable
fun DocumentoScreen(
    tipo: TipoDoc,
    docId: Long?,
    onVolver: () -> Unit,
    onAbrirFactura: (Long) -> Unit,
    viewModel: DocumentoViewModel = viewModel()
) {
    LaunchedEffect(tipo, docId) {
        viewModel.iniciar(tipo)
        if (docId != null) viewModel.abrir(docId)
    }
    val proveedores by viewModel.proveedores.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val pendientes by viewModel.pendientes.collectAsState()
    val abierto = viewModel.abierto
    val proveedorId = viewModel.proveedorId

    var mostrarNuevo by rememberSaveable { mutableStateOf(false) }
    var mostrarPasar by rememberSaveable { mutableStateOf(false) }
    var textoBusqueda by rememberSaveable { mutableStateOf("") }
    var altaRapidaValor by rememberSaveable { mutableStateOf<String?>(null) }
    val focusBusqueda = remember { FocusRequester() }

    fun buscar() {
        val q = textoBusqueda.trim()
        if (q.isEmpty()) return
        viewModel.buscar(q, onNoEncontrado = { altaRapidaValor = q })
        textoBusqueda = ""
    }

    // imePadding: con el teclado abierto la lista se encoge por encima de él y se desplaza hasta las últimas líneas.
    Column(modifier = Modifier.fillMaxSize().imePadding().padding(24.dp)) {
        // Primera línea: título (con número y fecha si hay uno abierto) a la izquierda, Nuevo y Volver a la derecha.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tipo.nombre, style = MaterialTheme.typography.headlineSmall)
                abierto?.let { Text("Nº ${it.numero.ifBlank { "—" }} · ${fechaDocumento(it.fecha)}", style = MaterialTheme.typography.bodySmall) }
            }
            // Con un documento abierto (nuevo o recuperado) Nuevo no hace nada: hay que volver a la lista antes.
            Button(onClick = { mostrarNuevo = true }, enabled = proveedorId != null && abierto == null) { Text("Nuevo") }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { if (abierto != null) viewModel.salirDeDocumento() else onVolver() }) { Text("Volver") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Segunda línea: desplegable de proveedor y, mientras no haya uno elegido, el aviso.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SelectorDropdown(
                label = "Proveedor",
                opciones = proveedores.map { it.id to it.nombre },
                seleccionado = proveedorId,
                onSeleccionar = { viewModel.seleccionarProveedor(it) },
                modifier = Modifier.widthIn(max = 320.dp)
            )
            if (proveedorId == null) {
                Spacer(modifier = Modifier.width(16.dp))
                Text("Selecciona proveedor", style = MaterialTheme.typography.bodyLarge)
            }
            if (abierto != null) {
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedTextField(
                    value = textoBusqueda,
                    onValueChange = { textoBusqueda = it.replace("\n", "") },
                    label = { Text("SKU, REF o código de barras") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { buscar() }),
                    modifier = Modifier.width(320.dp).mostrarTecladoEnPantalla().focusRequester(focusBusqueda)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (abierto != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Importe("Base", viewModel.base, Modifier.weight(1f))
                Importe("21% IVA", viewModel.iva, Modifier.weight(1f))
                Importe("5,20% R.E.", viewModel.recargo, Modifier.weight(1f))
                Importe("Total €", viewModel.total, Modifier.weight(1f))
                if (tipo == TipoDoc.ALBARAN) {
                    OutlinedButton(onClick = { mostrarPasar = true }, enabled = viewModel.lineas.isNotEmpty()) { Text("Pasar a factura") }
                }
            }
            LaunchedEffect(abierto.docId) { runCatching { focusBusqueda.requestFocus() } }
        }
        viewModel.error?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(8.dp))

        when {
            proveedorId == null -> {}
            abierto == null -> {
                if (pendientes.isEmpty()) {
                    Text("Este proveedor no tiene ${tipo.nombre.lowercase()}s pendientes. Pulsa Nuevo para empezar uno.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "${tipo.nombre}s pendientes (${pendientes.size}) — pulsa uno para abrirlo",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(pendientes, key = { it.id }) { d ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.abrir(d.id) }.padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Nº ${d.numero.ifBlank { "—" }} · ${fechaDocumento(d.fecha)}", style = MaterialTheme.typography.bodyLarge)
                                Text("%.2f €".format(d.total), style = MaterialTheme.typography.bodyLarge)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            else -> {
                if (viewModel.lineas.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("Artículo", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelLarge)
                        Text("Cantidad", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text("Precio (base)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text("Importe", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                    HorizontalDivider()
                }
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(viewModel.lineas.toList(), key = { it.producto.id }) { linea ->
                        val importe = (linea.cantidad.toIntOrNull() ?: 0) * (linea.coste.replace(',', '.').toDoubleOrNull() ?: 0.0)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                listOfNotNull(linea.producto.sku ?: linea.producto.referenciaFabricante, linea.producto.nombre).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = linea.cantidad,
                                onValueChange = { linea.cantidad = it; viewModel.lineaCambiada(linea) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).mostrarTecladoEnPantalla()
                            )
                            OutlinedTextField(
                                value = linea.coste,
                                onValueChange = { linea.coste = it; viewModel.lineaCambiada(linea) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).mostrarTecladoEnPantalla()
                            )
                            Text("%.2f €".format(importe), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.quitar(linea) }, modifier = Modifier.width(48.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Quitar")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (mostrarNuevo) {
        DatosDocumentoDialog(
            titulo = "Nuevo ${tipo.nombre.lowercase()}", numeroInicial = "", textoAceptar = "Crear",
            onAceptar = { numero, fecha -> viewModel.nuevo(numero, fecha) { mostrarNuevo = false } },
            onDismiss = { mostrarNuevo = false; viewModel.limpiarError() },
            error = viewModel.error
        )
    }
    if (mostrarPasar) {
        DatosDocumentoDialog(
            titulo = "Pasar a factura", numeroInicial = abierto?.numero.orEmpty(), textoAceptar = "Pasar",
            onAceptar = { numero, fecha -> viewModel.pasarAFactura(numero, fecha, null) { id -> mostrarPasar = false; onAbrirFactura(id) } },
            onDismiss = { mostrarPasar = false; viewModel.limpiarError() },
            error = viewModel.error
        )
        viewModel.duplicadoFactura?.let { (numero, fecha) ->
            AlertDialog(
                onDismissRequest = viewModel::cancelarDuplicadoFactura,
                title = { Text("Ya existe la factura $numero") },
                text = { Text("Este proveedor ya tiene una factura con ese número. ¿Quieres añadir las líneas de este albarán a ella?") },
                confirmButton = {
                    TextButton(onClick = { viewModel.pasarAFactura(numero, fecha, true) { id -> mostrarPasar = false; onAbrirFactura(id) } }) { Text("Fusionar") }
                },
                dismissButton = { TextButton(onClick = viewModel::cancelarDuplicadoFactura) { Text("Cancelar") } }
            )
        }
    }
    altaRapidaValor?.let { valor ->
        AltaRapidaDocDialog(
            valorBuscado = valor, proveedorId = proveedorId, categorias = categorias, proveedores = proveedores, viewModel = viewModel,
            onDismiss = { altaRapidaValor = null; viewModel.limpiarError() }
        )
    }
}

@Composable
private fun Importe(etiqueta: String, valor: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall)
        Text("%.2f €".format(valor), style = MaterialTheme.typography.titleMedium)
    }
}

/** Número y fecha del documento (la fecha se pide al crearlo y ya no se cambia). */
@Composable
fun DatosDocumentoDialog(
    titulo: String,
    numeroInicial: String,
    textoAceptar: String,
    onAceptar: (numero: String, fecha: String) -> Unit,
    onDismiss: () -> Unit,
    error: String?
) {
    var numero by rememberSaveable { mutableStateOf(numeroInicial) }
    var fecha by rememberSaveable { mutableStateOf(hoyFecha()) }
    var errorLocal by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column(modifier = Modifier.widthIn(min = 280.dp, max = 420.dp)) {
                LabeledTextField(label = "Número", value = numero, onValueChange = { numero = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                LabeledTextField(label = "Fecha *", value = fecha, onValueChange = { fecha = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                (errorLocal ?: error)?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!esFechaValida(fecha.trim())) errorLocal = "La fecha no es válida (dd-MM-aaaa)" else { errorLocal = null; onAceptar(numero.trim(), fecha.trim()) }
            }) { Text(textoAceptar) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AltaRapidaDocDialog(
    valorBuscado: String,
    proveedorId: Long?,
    categorias: List<CategoriaEntity>,
    proveedores: List<ProveedorEntity>,
    viewModel: DocumentoViewModel,
    onDismiss: () -> Unit
) {
    val esCodigoBarras = pareceCodigoBarras(valorBuscado)
    val plantilla = ProductoEntity(
        codigoBarras = if (esCodigoBarras) valorBuscado else null,
        referenciaFabricante = if (esCodigoBarras) null else valorBuscado,
        nombre = "", proveedorId = proveedorId, fechaCreacion = "", fechaActualizacion = ""
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface {
            Card(modifier = Modifier.padding(24.dp)) {
                ProductoForm(
                    titulo = "Artículo no encontrado — Nuevo artículo", inicial = plantilla, categorias = categorias,
                    proveedores = proveedores, error = viewModel.error, onCancelar = onDismiss, labelExistencia = "Cantidad recibida"
                ) { sku, codigoBarras, ref, nombre, categoriaId, prov, coste, margen, pvp, existencia, minimo, maximo, teclado ->
                    viewModel.crearProductoYAgregar(sku, codigoBarras, ref, nombre, categoriaId, prov, coste, margen, pvp, existencia, minimo, maximo, teclado) { onDismiss() }
                }
            }
        }
    }
}
