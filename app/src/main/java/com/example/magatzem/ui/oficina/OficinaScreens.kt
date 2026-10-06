package com.example.magatzem.ui.oficina

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.example.magatzem.data.ESTADO_FACTURA_PAGADA
import com.example.magatzem.data.ESTADO_FACTURA_PENDIENTE
import com.example.magatzem.data.FormaPagoEntity
import com.example.magatzem.data.PagoFacturaEntity
import com.example.magatzem.data.PedidoEntity
import com.example.magatzem.data.NOMBRE_CONTADO
import com.example.magatzem.data.esBanco
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown
import com.example.magatzem.ui.common.claveOrdenFecha
import com.example.magatzem.ui.common.esFechaValida
import com.example.magatzem.ui.common.fechaDocumento
import com.example.magatzem.ui.common.fechaMasDias
import com.example.magatzem.ui.common.formatearFecha
import com.example.magatzem.ui.common.hoyFecha
import com.example.magatzem.ui.pedidos.PedidoViewModel
import java.util.Locale
import kotlin.math.abs

private fun euros(valor: Double) = "%.2f €".format(valor)

@Composable
private fun CabeceraOficina(titulo: String, extra: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = titulo, style = MaterialTheme.typography.headlineSmall)
        extra()
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
}

/** Oficina → Pedidos: los pedidos pendientes; al pulsar uno se ofrece eliminarlo. */
@Composable
fun OficinaPedidosScreen(viewModel: PedidoViewModel = viewModel()) {
    val pedidos by viewModel.pedidos.collectAsState()
    val proveedores by viewModel.proveedores.collectAsState()
    val nombre = remember(proveedores) { proveedores.associate { it.id to it.nombre } }
    val pendientes = remember(pedidos) { pedidos.filter { it.estado == "pendiente" } }
    var seleccionado by remember { mutableStateOf<PedidoEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CabeceraOficina("Pedidos pendientes")
        if (pendientes.isEmpty()) {
            Text("No hay pedidos pendientes.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pendientes, key = { it.id }) { pedido ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { seleccionado = pedido }.padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(nombre[pedido.proveedorId] ?: "Sin proveedor", style = MaterialTheme.typography.bodyLarge)
                            Text(formatearFecha(pedido.fecha), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Total ${euros(pedido.total)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    seleccionado?.let { pedido ->
        AlertDialog(
            onDismissRequest = { seleccionado = null },
            title = { Text("Pedido") },
            text = {
                Text(
                    "${nombre[pedido.proveedorId] ?: "Sin proveedor"} · ${formatearFecha(pedido.fecha)} · " +
                        "Total ${euros(pedido.total)}\n\n¿Eliminar este pedido?"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.eliminar(pedido); seleccionado = null }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { seleccionado = null }) { Text("Cancelar") } }
        )
    }
}

/** Oficina → Albaranes: los albaranes pendientes de facturar, con "Pasar a factura" (pendiente de pago). */
@Composable
fun OficinaAlbaranesScreen(viewModel: OficinaDocumentosViewModel = viewModel(), onEditar: (Long) -> Unit = {}) {
    val albaranes by viewModel.albaranes.collectAsState()
    var pasando by remember { mutableStateOf<AlbaranFila?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CabeceraOficina("Albaranes pendientes")
        if (albaranes.isEmpty()) {
            Text("No hay albaranes pendientes de pasar a factura.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(albaranes, key = { it.albaran.id }) { fila ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onEditar(fila.albaran.id) }.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(fila.proveedor, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Nº ${fila.albaran.numero ?: "—"} · ${fechaDocumento(fila.albaran.fecha)} · " +
                                    "Base ${euros(fila.base)} · IVA ${euros(fila.iva)} · Total ${euros(fila.base + fila.iva)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { pasando = fila }) { Text("Pasar a factura") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    pasando?.let { fila ->
        PasarAFacturaDialog(
            fila = fila,
            onConfirm = { numero, fecha ->
                viewModel.pasarAFactura(fila, numero, fecha)
                pasando = null
            },
            onDismiss = { pasando = null }
        )
    }
    viewModel.duplicadoFactura?.let { d ->
        AlertDialog(
            onDismissRequest = { viewModel.resolverDuplicadoFactura(null) },
            title = { Text("Ya existe una factura con ese número") },
            text = {
                Text(
                    "${d.fila.proveedor} ya tiene la factura ${d.numero} (${fechaDocumento(d.existente.fecha)}). " +
                        if (d.tienePagos) {
                            "Tiene pagos registrados y no se puede fusionar: se puede crear aparte."
                        } else {
                            "¿Quieres fusionar este albarán con ella? Sus líneas e importes se sumarán a la factura existente."
                        }
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { viewModel.resolverDuplicadoFactura(false) }) { Text("Crear aparte") }
                    if (!d.tienePagos) TextButton(onClick = { viewModel.resolverDuplicadoFactura(true) }) { Text("Fusionar") }
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.resolverDuplicadoFactura(null) }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PasarAFacturaDialog(fila: AlbaranFila, onConfirm: (numero: String, fecha: String) -> Unit, onDismiss: () -> Unit) {
    var numero by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(hoyFecha()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pasar a factura") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${fila.proveedor} · total ${euros(fila.base + fila.iva)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "La factura queda pendiente de pago y el albarán se borra.",
                    style = MaterialTheme.typography.bodySmall
                )
                LabeledTextField(label = "Número de factura", value = numero, onValueChange = { numero = it }, singleLine = true)
                LabeledTextField(label = "Fecha (dd-MM-aaaa)", value = fecha, onValueChange = { fecha = it }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    numero.isBlank() -> error = "Indica el número de factura"
                    !esFechaValida(fecha.trim()) -> error = "La fecha no es válida (dd-MM-aaaa)"
                    else -> onConfirm(numero, fecha)
                }
            }) { Text("Crear factura") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Oficina → Facturas: por proveedor, pendientes o pagadas. Una pendiente se paga de una vez (contado o
 * transferencia, con su fecha) o por banco en 1, 2 o 3 plazos (importe y fecha de cada uno); los plazos
 * se dan por pagados con su fecha pulsándolos.
 */
@Composable
fun OficinaFacturasScreen(viewModel: OficinaDocumentosViewModel = viewModel(), onEditar: (Long) -> Unit = {}) {
    val todas by viewModel.facturas.collectAsState()
    val formas by viewModel.formasPagoActivas.collectAsState()
    var verPagadas by remember { mutableStateOf(false) }
    val estado = if (verPagadas) ESTADO_FACTURA_PAGADA else ESTADO_FACTURA_PENDIENTE
    val filas = remember(todas, estado) { todas.filter { it.factura.estado == estado } }
    val porProveedor = remember(filas) { filas.groupBy { it.proveedor } }
    var pagando by remember { mutableStateOf<FacturaFila?>(null) }
    var plazoSeleccionado by remember { mutableStateOf<PagoFacturaEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CabeceraOficina("Facturas") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !verPagadas, onClick = { verPagadas = false }, label = { Text("Pendientes") })
                FilterChip(selected = verPagadas, onClick = { verPagadas = true }, label = { Text("Pagadas") })
            }
        }
        if (filas.isEmpty()) {
            Text(
                if (verPagadas) "No hay facturas pagadas." else "No hay facturas pendientes de pago.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                porProveedor.forEach { (proveedor, lista) ->
                    item(key = "prov_$proveedor") {
                        Text(
                            "$proveedor · ${euros(lista.sumOf { it.total })}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(lista.sortedBy { claveOrdenFecha(it.factura.fecha) }, key = { it.factura.id }) { fila ->
                        val f = fila.factura
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).clickable { onEditar(f.id) }) {
                                    Text(
                                        "Nº ${f.numero ?: "—"} · ${fechaDocumento(f.fecha)} · Total ${euros(fila.total)}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "Base ${euros(f.base)} · IVA ${euros(f.iva)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row {
                                    if (!verPagadas && fila.pagos.isEmpty()) {
                                        TextButton(onClick = { pagando = fila }) { Text("Pagar") }
                                    }
                                    if (!verPagadas && fila.pagos.isNotEmpty() && fila.pagos.none { it.fechaPago != null }) {
                                        TextButton(onClick = { viewModel.anularPlan(f) }) { Text("Anular plan") }
                                    }
                                }
                            }
                            fila.pagos.forEach { pago ->
                                Text(
                                    buildString {
                                        append(if (pago.totalPlazos > 1) "Plazo ${pago.numeroPlazo}/${pago.totalPlazos}" else "Pago")
                                        append(" · ").append(fila.formaPorPago[pago.formaPagoId ?: -1L] ?: "—")
                                        append(" · ").append(euros(pago.importe))
                                        if (pago.fechaPago != null) append(" · pagado el ").append(fechaDocumento(pago.fechaPago))
                                        else append(" · pendiente, previsto ").append(fechaDocumento(pago.fechaPrevista))
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (pago.fechaPago != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().clickable { plazoSeleccionado = pago }.padding(vertical = 6.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    val aviso by viewModel.aviso.collectAsState()
    aviso?.let { texto ->
        AlertDialog(
            onDismissRequest = viewModel::cerrarAviso,
            title = { Text("No se puede pagar") },
            text = { Text(texto) },
            confirmButton = { TextButton(onClick = viewModel::cerrarAviso) { Text("Aceptar") } }
        )
    }
    pagando?.let { fila ->
        PagarFacturaDialog(
            fila = fila,
            // El efectivo se maneja solo desde MiTPV (pago a proveedor con nº de factura): aquí no se ofrece "Contado".
            formas = formas.filter { it.nombre != NOMBRE_CONTADO },
            onPagarDeUnaVez = { formaId, fecha -> viewModel.pagarDeUnaVez(fila.factura, formaId, fecha); pagando = null },
            onPlanificar = { formaId, plazos -> viewModel.planificarBanco(fila.factura, formaId, plazos); pagando = null },
            onDismiss = { pagando = null }
        )
    }
    plazoSeleccionado?.let { pago ->
        PlazoDialog(
            pago = pago,
            onMarcarPagado = { viewModel.marcarPagado(pago, it); plazoSeleccionado = null },
            onPasarAPendiente = { viewModel.pasarAPendiente(pago); plazoSeleccionado = null },
            onDismiss = { plazoSeleccionado = null }
        )
    }
}

/** Reparte [total] en [n] importes en céntimos; el último lleva el resto. */
private fun repartir(total: Double, n: Int): List<String> {
    val centimos = Math.round(total * 100)
    val parte = centimos / n
    return List(n) { i ->
        val c = if (i == n - 1) centimos - parte * (n - 1) else parte
        String.format(Locale.US, "%.2f", c / 100.0)
    }
}

private fun parseImporte(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()

@Composable
private fun PagarFacturaDialog(
    fila: FacturaFila,
    formas: List<FormaPagoEntity>,
    onPagarDeUnaVez: (formaPagoId: Long, fecha: String) -> Unit,
    onPlanificar: (formaPagoId: Long, plazos: List<Pair<Double, String>>) -> Unit,
    onDismiss: () -> Unit
) {
    val total = fila.total
    var formaId by remember { mutableStateOf<Long?>(null) }
    var fecha by remember { mutableStateOf(hoyFecha()) }
    var nPlazos by remember { mutableStateOf(1) }
    var importes by remember { mutableStateOf(repartir(total, 1)) }
    var fechas by remember { mutableStateOf(listOf(fechaMasDias(30))) }
    var error by remember { mutableStateOf<String?>(null) }
    val esBanco = formas.firstOrNull { it.id == formaId }?.esBanco() == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pagar factura") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("${fila.proveedor} · Nº ${fila.factura.numero ?: "—"} · total ${euros(total)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Los pagos al contado se hacen desde MiTPV, indicando el proveedor y el número de factura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectorDropdown(
                    label = "Forma de pago",
                    opciones = formas.map { it.id as Long? to it.nombre },
                    seleccionado = formaId,
                    onSeleccionar = { formaId = it }
                )
                if (formaId != null && !esBanco) {
                    LabeledTextField(label = "Fecha de pago (dd-MM-aaaa)", value = fecha, onValueChange = { fecha = it }, singleLine = true)
                }
                if (esBanco) {
                    SelectorDropdown(
                        label = "Plazos",
                        opciones = listOf(1L, 2L, 3L).map { it as Long? to "$it" },
                        seleccionado = nPlazos.toLong(),
                        onSeleccionar = {
                            nPlazos = (it ?: 1L).toInt()
                            importes = repartir(total, nPlazos)
                            fechas = List(nPlazos) { i -> fechaMasDias(30 * (i + 1)) }
                        }
                    )
                    for (i in 0 until nPlazos) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledTextField(
                                label = "Plazo ${i + 1}: importe €",
                                value = importes[i],
                                onValueChange = { v -> importes = importes.toMutableList().also { it[i] = v } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            LabeledTextField(
                                label = "Fecha (dd-MM-aaaa)",
                                value = fechas[i],
                                onValueChange = { v -> fechas = fechas.toMutableList().also { it[i] = v } },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val id = formaId
                when {
                    id == null -> error = "Elige la forma de pago"
                    !esBanco -> if (esFechaValida(fecha.trim())) onPagarDeUnaVez(id, fecha.trim())
                    else error = "La fecha no es válida (dd-MM-aaaa)"
                    else -> {
                        val valores = importes.take(nPlazos).map { parseImporte(it) }
                        when {
                            valores.any { it == null || it <= 0.0 } -> error = "Revisa los importes de los plazos"
                            fechas.take(nPlazos).any { !esFechaValida(it.trim()) } -> error = "Revisa las fechas de los plazos (dd-MM-aaaa)"
                            abs(valores.sumOf { it!! } - total) > 0.005 ->
                                error = "Los plazos suman ${euros(valores.sumOf { it!! })} y la factura es de ${euros(total)}"
                            else -> onPlanificar(id, valores.mapIndexed { i, v -> v!! to fechas[i].trim() })
                        }
                    }
                }
            }) { Text(if (esBanco) "Guardar plazos" else "Pagar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Un pago o plazo: darlo por pagado con su fecha, cambiar esa fecha o devolverlo a pendiente. */
@Composable
private fun PlazoDialog(
    pago: PagoFacturaEntity,
    onMarcarPagado: (fecha: String) -> Unit,
    onPasarAPendiente: () -> Unit,
    onDismiss: () -> Unit
) {
    var fecha by remember { mutableStateOf(pago.fechaPago?.let { fechaDocumento(it) } ?: hoyFecha()) }
    var error by remember { mutableStateOf<String?>(null) }
    val pagado = pago.fechaPago != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pago.totalPlazos > 1) "Plazo ${pago.numeroPlazo} de ${pago.totalPlazos}" else "Pago") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${euros(pago.importe)} · previsto ${fechaDocumento(pago.fechaPrevista)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                LabeledTextField(label = "Fecha de pago (dd-MM-aaaa)", value = fecha, onValueChange = { fecha = it }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (pagado) {
                    TextButton(onClick = onPasarAPendiente) { Text("Pasar a pendiente") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (esFechaValida(fecha.trim())) onMarcarPagado(fecha.trim()) else error = "La fecha no es válida (dd-MM-aaaa)"
            }) { Text(if (pagado) "Guardar fecha" else "Marcar pagado") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
