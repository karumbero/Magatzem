package com.example.magatzem.ui.entradas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.magatzem.data.factorCoste
import com.example.magatzem.data.factorIva
import com.example.magatzem.data.factorRecargo
import com.example.magatzem.data.PedidoEntity
import com.example.magatzem.ui.common.AvisoDialog
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.esFechaValida
import com.example.magatzem.ui.common.fechaDocumento
import com.example.magatzem.ui.common.formatearFecha
import com.example.magatzem.ui.common.hoyFecha
import com.example.magatzem.ui.common.mostrarTecladoEnPantalla
import com.example.magatzem.ui.common.pareceCodigoBarras
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.ui.common.SelectorDropdown
import com.example.magatzem.ui.productos.ProductoForm

/**
 * Movimientos → Entradas. Lo primero es la lista de pedidos pendientes: al pulsar uno se abre para
 * verificar/modificar unidades y precios, pasarlo a albarán o factura y guardar (el pedido se borra).
 * "Entrada sin pedido" abre el mismo formulario vacío.
 */
@Composable
fun EntradaScreen(viewModel: EntradaViewModel = viewModel()) {
    var mensaje by rememberSaveable { mutableStateOf<String?>(null) }
    if (viewModel.enFormulario) {
        EntradaFormulario(viewModel, mensaje, onMensaje = { mensaje = it })
    } else {
        PedidosPendientesLista(viewModel, mensaje, onAbrir = { mensaje = null; viewModel.abrirPedido(it) },
            onNueva = { mensaje = null; viewModel.nuevaEntrada() })
    }
}

@Composable
private fun PedidosPendientesLista(
    viewModel: EntradaViewModel,
    mensaje: String?,
    onAbrir: (PedidoEntity) -> Unit,
    onNueva: () -> Unit
) {
    val pedidos by viewModel.pedidosPendientes.collectAsState()
    val proveedores by viewModel.proveedores.collectAsState()
    val nombre = remember(proveedores) { proveedores.associate { it.id to it.nombre } }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Entradas · pedidos pendientes", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onNueva) { Text("Entrada sin pedido") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        mensaje?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (pedidos.isEmpty()) {
            Text("No hay pedidos pendientes. Usa \"Entrada sin pedido\" para registrar una entrada.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Pulsa un pedido para abrirlo y registrar su entrada.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pedidos, key = { it.id }) { pedido ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAbrir(pedido) }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(nombre[pedido.proveedorId] ?: "Sin proveedor", style = MaterialTheme.typography.bodyLarge)
                            Text(formatearFecha(pedido.fecha), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Total %.2f €".format(pedido.total), style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EntradaFormulario(viewModel: EntradaViewModel, mensaje: String?, onMensaje: (String?) -> Unit) {
    val proveedores by viewModel.proveedores.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    var textoBusqueda by rememberSaveable { mutableStateOf("") }
    var altaRapidaValor by rememberSaveable { mutableStateOf<String?>(null) }
    var avisoSeleccioneProveedor by rememberSaveable { mutableStateOf(false) }
    // Producto encontrado pero registrado bajo otro proveedor (ver el diálogo "Producto existente").
    var productoOtroProveedor by remember { mutableStateOf<ProductoEntity?>(null) }
    // Tipo de documento cuyo diálogo está abierto ahora mismo; null = cerrado.
    var tipoDocumentoAEditar by rememberSaveable { mutableStateOf<TipoDocumento?>(null) }

    // Ya existe un albarán/factura de este proveedor con el mismo número: ¿fusionar?
    viewModel.duplicado?.let { d ->
        val nombreDoc = if (d.tipo == TipoDocumento.FACTURA) "la factura" else "el albarán"
        AlertDialog(
            onDismissRequest = { viewModel.resolverDuplicado(null) },
            title = { Text("Ya existe ${if (d.tipo == TipoDocumento.FACTURA) "una factura" else "un albarán"} con ese número") },
            text = {
                Text(
                    "Este proveedor ya tiene $nombreDoc ${d.numero} (${fechaDocumento(d.fecha)}). " +
                        if (d.tienePagos) {
                            "Tiene pagos registrados y no se puede fusionar: se puede guardar aparte."
                        } else {
                            "¿Quieres fusionar esta entrada con ${if (d.tipo == TipoDocumento.FACTURA) "ella" else "él"}? " +
                                "Las líneas y los importes se sumarán al documento existente."
                        }
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { viewModel.resolverDuplicado(false) }) { Text("Guardar aparte") }
                    if (!d.tienePagos) TextButton(onClick = { viewModel.resolverDuplicado(true) }) { Text("Fusionar") }
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.resolverDuplicado(null) }) { Text("Cancelar") } }
        )
    }

    val proveedorPorId = remember(proveedores) { proveedores.associateBy { it.id } }

    val focusReferencia = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusReferencia.requestFocus() }

    fun buscar() {
        val query = textoBusqueda.trim()
        if (query.isEmpty()) return
        if (viewModel.proveedorSeleccionadoId == null) {
            // Sin proveedor no se sabe a quién facturar la entrada; se avisa y se deja el valor
            // tecleado/escaneado tal cual, para no tener que volver a escribirlo o escanearlo.
            avisoSeleccioneProveedor = true
            return
        }
        onMensaje(null)
        viewModel.buscar(
            query,
            onNoEncontrado = { altaRapidaValor = query },
            onProveedorDistinto = { producto -> productoOtroProveedor = producto }
        )
        textoBusqueda = ""
    }

    // Base = suma de cantidad x precio(base) de las líneas; IVA y recargo se calculan sobre esa base
    // una sola vez para toda la recepción (ya no por línea), y todo esto va arriba del todo, junto a
    // los botones, para que no quede tapado por el teclado en pantalla al escribir en un campo.
    val subtotalBase = viewModel.lineas.sumOf { linea ->
        val coste = linea.coste.replace(',', '.').toDoubleOrNull() ?: 0.0
        val cantidad = linea.cantidad.toIntOrNull() ?: 0
        cantidad * coste
    }
    val exento = proveedorPorId[viewModel.proveedorSeleccionadoId]?.exentoIva == true
    val importeIvaTotal = subtotalBase * (factorIva(exento) - 1)
    val importeRecargoTotal = subtotalBase * factorRecargo(exento)
    val totalFinal = subtotalBase * factorCoste(exento)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Entradas", style = MaterialTheme.typography.headlineSmall)
            Row {
                BotonTipoDocumento(
                    texto = "Albarán",
                    seleccionado = viewModel.documento?.tipo == TipoDocumento.ALBARAN,
                    onClick = { tipoDocumentoAEditar = TipoDocumento.ALBARAN }
                )
                Spacer(modifier = Modifier.width(8.dp))
                BotonTipoDocumento(
                    texto = "Factura",
                    seleccionado = viewModel.documento?.tipo == TipoDocumento.FACTURA,
                    onClick = { tipoDocumentoAEditar = TipoDocumento.FACTURA }
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ResumenImporte("Subtotal (base)", subtotalBase, modifier = Modifier.weight(1f))
            ResumenImporte("21% IVA", importeIvaTotal, modifier = Modifier.weight(1f))
            ResumenImporte("5,20% R.E.", importeRecargoTotal, modifier = Modifier.weight(1f))
            ResumenImporte("Total €", totalFinal, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {
                viewModel.confirmar { onMensaje("Entrada registrada correctamente") }
            }) {
                Text("Guardar")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = {
                viewModel.cancelar()
                textoBusqueda = ""
                onMensaje(null)
                focusReferencia.requestFocus()
            }) {
                Text("Cancelar")
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            SelectorDropdown(
                label = "Proveedor",
                opciones = proveedores.map { it.id to it.nombre },
                seleccionado = viewModel.proveedorSeleccionadoId,
                onSeleccionar = {
                    viewModel.seleccionarProveedor(it)
                    focusReferencia.requestFocus()
                    // Si ya había algo escrito/escaneado esperando proveedor, se busca ahora sin más pasos.
                    if (textoBusqueda.isNotBlank()) buscar()
                },
                modifier = Modifier.width(264.dp) // 220dp + 20%
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = { textoBusqueda = it.replace("\n", "") },
                label = { Text("SKU, REF o código de barras") },
                // singleLine es imprescindible: sin él, el Enter del lector inserta un salto de línea
                // en vez de disparar onDone. Solo Enter dispara la búsqueda; no hay botón "Buscar".
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { buscar() }),
                modifier = Modifier.width(280.dp).mostrarTecladoEnPantalla().focusRequester(focusReferencia)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        viewModel.error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        mensaje?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.lineas.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("Artículo", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelLarge)
                Text("Cantidad", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Precio (base)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Importe", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("", modifier = Modifier.width(48.dp))
            }
            HorizontalDivider()
        }

        viewModel.documentoRecuperado?.let { rec ->
            val nombreDoc = if (rec.tipo == TipoDocumento.ALBARAN) "Albarán" else "Factura"
            Text(
                "$nombreDoc ${rec.numero} abierto (${fechaDocumento(rec.fecha)}): puedes cambiar sus líneas y seguir entrando artículos. " +
                    "Guardar aplica los cambios a las existencias.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(viewModel.lineas, key = { _, linea -> linea.producto.id }) { index, linea ->
                val costeValor = linea.coste.replace(',', '.').toDoubleOrNull() ?: 0.0
                val cantidadValor = linea.cantidad.toIntOrNull() ?: 0
                val importe = cantidadValor * costeValor

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = listOfNotNull(linea.producto.sku ?: linea.producto.referenciaFabricante, linea.producto.nombre)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = linea.cantidad,
                        onValueChange = { viewModel.actualizarLinea(index, cantidad = it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).mostrarTecladoEnPantalla()
                    )
                    OutlinedTextField(
                        value = linea.coste,
                        onValueChange = { viewModel.actualizarLinea(index, coste = it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).mostrarTecladoEnPantalla()
                    )
                    Text(
                        text = "%.2f €".format(importe),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.quitarLinea(index) }, modifier = Modifier.width(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Quitar")
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (avisoSeleccioneProveedor) {
        AvisoDialog(
            titulo = "Falta el proveedor",
            mensaje = "Seleccione proveedor",
            onDismiss = { avisoSeleccioneProveedor = false }
        )
    }

    productoOtroProveedor?.let { producto ->
        val nombreProveedorReal = proveedorPorId[producto.proveedorId]?.nombre ?: "sin proveedor"
        AlertDialog(
            onDismissRequest = {
                productoOtroProveedor = null
                focusReferencia.requestFocus()
            },
            title = { Text("Artículo existente") },
            text = {
                Text(
                    "${producto.sku} · ${producto.nombre} ya existe, pero de \"$nombreProveedorReal\", " +
                        "no del proveedor seleccionado."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    productoOtroProveedor = null
                    focusReferencia.requestFocus()
                }) { Text("Aceptar") }
            }
        )
    }

    altaRapidaValor?.let { valorBuscado ->
        AltaRapidaDialog(
            valorBuscado = valorBuscado,
            proveedorId = viewModel.proveedorSeleccionadoId,
            categorias = categorias,
            proveedores = proveedores,
            viewModel = viewModel,
            onDismiss = {
                altaRapidaValor = null
                viewModel.limpiarError()
                focusReferencia.requestFocus()
            }
        )
    }

    tipoDocumentoAEditar?.let { tipo ->
        DocumentoDialog(
            tipo = tipo,
            inicial = viewModel.documento?.takeIf { it.tipo == tipo },
            baseSugerida = viewModel.calcularBaseActual(),
            exentoIva = exento,
            recuperado = viewModel.vistaPrevia,
            onNumeroCambia = { viewModel.comprobarNumero(tipo, it) },
            onGuardar = { datos ->
                viewModel.guardarDocumento(datos)
                tipoDocumentoAEditar = null
                focusReferencia.requestFocus()
            },
            onDismiss = {
                viewModel.descartarVistaPrevia()
                tipoDocumentoAEditar = null
                focusReferencia.requestFocus()
            }
        )
    }
}

/** Una cifra del resumen (Subtotal/IVA/R.E./Total) con su etiqueta encima, en la fila superior. */
@Composable
private fun ResumenImporte(etiqueta: String, importe: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = etiqueta, style = MaterialTheme.typography.labelSmall)
        Text(text = "%.2f €".format(importe), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BotonTipoDocumento(texto: String, seleccionado: Boolean, onClick: () -> Unit, habilitado: Boolean = true) {
    if (seleccionado) {
        Button(onClick = onClick, enabled = habilitado, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(texto) }
    } else {
        OutlinedButton(onClick = onClick, enabled = habilitado, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(texto) }
    }
}

@Composable
private fun DocumentoDialog(
    tipo: TipoDocumento,
    inicial: DatosDocumento?,
    baseSugerida: Double,
    exentoIva: Boolean,
    recuperado: EntradaViewModel.DocumentoRecuperado?,
    onNumeroCambia: (String) -> Unit,
    onGuardar: (DatosDocumento) -> Unit,
    onDismiss: () -> Unit
) {
    var numero by rememberSaveable { mutableStateOf(inicial?.numero.orEmpty()) }
    LaunchedEffect(numero) { onNumeroCambia(numero) }
    val existente = recuperado?.takeIf { it.numero == numero.trim() && it.tipo == tipo }
    var fecha by rememberSaveable { mutableStateOf(inicial?.fecha?.let { fechaDocumento(it) } ?: hoyFecha()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun intentarGuardar() {
        if (!esFechaValida(fecha.trim())) {
            error = "La fecha no es válida (dd-MM-aaaa)"
            return
        }
        // Base e IVA ya no se piden aquí: se calculan siempre de las líneas actuales (se ven en el
        // resumen de arriba de la pantalla) y se guardan tal cual junto al albarán/factura.
        val base = "%.2f".format(baseSugerida)
        val iva = "%.2f".format(baseSugerida * (factorIva(exentoIva) - 1))
        onGuardar(DatosDocumento(tipo, numero.trim(), fecha.trim(), base, iva))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (tipo) {
                    TipoDocumento.ALBARAN -> "Datos del albarán"
                    TipoDocumento.FACTURA -> "Datos de la factura"
                }
            )
        },
        text = {
            Column(modifier = Modifier.widthIn(min = 280.dp, max = 420.dp)) {
                LabeledTextField(
                    label = "Número",
                    value = numero,
                    onValueChange = { numero = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { intentarGuardar() }),
                    modifier = Modifier.fillMaxWidth()
                )
                existente?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Ya existe (${fechaDocumento(it.fecha)}): ${it.lineas.size} líneas, total ${"%.2f €".format(it.total)}. " +
                            "Pulsa Recuperar para abrirlo y seguir entrando artículos en él.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LabeledTextField(
                    label = "Fecha *",
                    value = fecha,
                    onValueChange = { fecha = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { intentarGuardar() }) { Text(if (existente != null) "Recuperar" else "Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AltaRapidaDialog(
    valorBuscado: String,
    proveedorId: Long?,
    categorias: List<CategoriaEntity>,
    proveedores: List<ProveedorEntity>,
    viewModel: EntradaViewModel,
    onDismiss: () -> Unit
) {
    // El SKU lo asigna la tienda, así que no se rellena con lo buscado: según su forma, se ofrece
    // como código de barras (si son 8/12/13 dígitos, como escanearía el lector) o como REF de fabricante.
    val esCodigoBarras = pareceCodigoBarras(valorBuscado)
    val plantilla = ProductoEntity(
        codigoBarras = if (esCodigoBarras) valorBuscado else null,
        referenciaFabricante = if (esCodigoBarras) null else valorBuscado,
        nombre = "",
        proveedorId = proveedorId,
        fechaCreacion = "",
        fechaActualizacion = ""
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface {
            Card(modifier = Modifier.padding(24.dp)) {
                ProductoForm(
                    titulo = "Artículo no encontrado — Nuevo artículo",
                    inicial = plantilla,
                    categorias = categorias,
                    proveedores = proveedores,
                    error = viewModel.error,
                    onCancelar = onDismiss,
                    labelExistencia = "Cantidad recibida"
                ) { skuForm, codigoBarras, referenciaFabricante, nombre, categoriaId, proveedorIdForm, coste, margen, precioVenta, existencia, minimo, maximo, mostrarEnTeclado ->
                    viewModel.crearProductoYAgregar(
                        skuForm, codigoBarras, referenciaFabricante, nombre, categoriaId, proveedorIdForm,
                        coste, margen, precioVenta, existencia, minimo, maximo, mostrarEnTeclado
                    ) {
                        onDismiss()
                    }
                }
            }
        }
    }
}
