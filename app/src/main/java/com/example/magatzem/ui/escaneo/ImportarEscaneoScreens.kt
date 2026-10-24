package com.example.magatzem.ui.escaneo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.escaneo.FacturaEscaneada
import com.example.magatzem.data.escaneo.ImportacionEscaneo
import com.example.magatzem.data.factorCoste
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown
import com.example.magatzem.ui.documentos.AvisoRemarcarDialog
import java.util.Locale

private fun eur(v: Double?) = if (v == null) "—" else String.format(Locale.getDefault(), "%.2f €", v)
private fun num(v: Double) = String.format(Locale.US, "%.2f", v).replace('.', ',')
private fun dec(t: String) = t.trim().replace(',', '.').toDoubleOrNull()
private fun fechaEs(iso: String) = iso.substring(8) + "-" + iso.substring(5, 7) + "-" + iso.substring(0, 4)

/** Se muestra encima de todo en cuanto Escaneo manda una factura (y hay sesión iniciada). */
@Composable
fun ImportarEscaneoDialogs(puedeProbar: Boolean = false, vm: ImportarEscaneoViewModel = viewModel()) {
    val f = ImportacionEscaneo.pendiente
    ImportacionEscaneo.error?.let { texto ->
        AlertDialog(onDismissRequest = { ImportacionEscaneo.error = null }, title = { Text("Escaneo") }, text = { Text(texto) },
            confirmButton = { TextButton(onClick = { ImportacionEscaneo.error = null }) { Text("Aceptar") } })
    }
    if (f == null) return
    vm.puedeProbar = puedeProbar
    vm.preparar(f)
    Dialog(onDismissRequest = { if (!vm.ocupado) vm.cerrar() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${if (f.esAlbaran) "Albarán" else "Factura"} de Escaneo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${f.proveedor.ifBlank { "(sin proveedor)" }} · Nº ${f.numero.ifBlank { "—" }} · ${fechaEs(f.fechaIso)} · total ${eur(f.total)}", style = MaterialTheme.typography.bodyLarge)
                if (!f.esAlbaran && f.plazos.isNotEmpty()) Text(
                    "Pago: ${f.formaPago.ifBlank { "—" }} · " + f.plazos.joinToString(" · ") { "${fechaEs(it.fechaIso)} ${eur(it.importe)}" }, style = MaterialTheme.typography.bodyMedium
                )
                if (vm.resultado == null) vm.avisoProveedor?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary) }
                vm.mensaje?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (it.startsWith("Todas")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                vm.resultado?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
                if (vm.resultado == null && vm.faltan > 0) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("⚠ ${vm.faltan} producto${if (vm.faltan == 1) "" else "s"} de esta factura no existe${if (vm.faltan == 1) "" else "n"} en Magatzem.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = vm.crearFaltantes, onCheckedChange = { vm.crearFaltantes = it })
                        Text("Crear los productos que faltan al dar entrada (SKU «${vm.proveedor?.id ?: "?"}/ref», nombre y precio de la factura; las unidades entran con la factura)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (vm.crearFaltantes) SelectorDropdown(label = "Categoría de los productos nuevos *", opciones = listOf<Pair<Long?, String>>(null to "—") + vm.categorias.map { it.id as Long? to it.nombre }, seleccionado = vm.categoriaFaltantes, onSeleccionar = { vm.categoriaFaltantes = it })
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(vm.lineas) { _, l ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${l.ref} · ${l.descripcion}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("${l.cantidad.toLong()} × ${eur(l.costeNeto)}" + (l.dto?.takeIf { it > 0 }?.let { " (dto. ${num(it)} %)" } ?: "") + " = ${eur((l.costeNeto ?: 0.0) * l.cantidad)}", style = MaterialTheme.typography.bodyMedium)
                            val p = l.producto
                            when (l.estado) {
                                EstadoLinea.NO_EXISTE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("⚠ No existe en Magatzem", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (vm.resultado == null) OutlinedButton(onClick = { vm.mensaje = null; vm.lineaNueva = l }) { Text("Añadir producto") }
                                }
                                EstadoLinea.COSTE_MAS_ALTO -> if (p != null) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("⚠ Coste más alto: base ${eur(l.costeNeto)} (en Magatzem ${eur(p.coste)})", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                    Text("PVP anterior ${eur(p.precioVenta)} · PVP recomendado ${eur(l.pvpRecomendado)} (coste nuevo + margen ${num(p.margenBeneficio)} %)", style = MaterialTheme.typography.bodySmall)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Switch(checked = l.actualizarPvp, onCheckedChange = { if (vm.resultado == null) l.actualizarPvp = it })
                                        Text(if (l.actualizarPvp) "Actualizar PVP a ${eur(l.pvpRecomendado)}" else "Mantener el PVP anterior")
                                    }
                                    if (p.existencia > 0) Text("Hay ${p.existencia} uds en Magatzem: al dar entrada, si se actualiza el PVP, esas unidades hay que remarcarlas.", style = MaterialTheme.typography.bodySmall)
                                }
                                EstadoLinea.EXISTE -> if (p != null) Text("✓ En Magatzem · SKU ${p.sku ?: "—"} · coste ${eur(p.coste)} · PVP ${eur(p.precioVenta)} · existencia ${p.existencia}", style = MaterialTheme.typography.bodySmall)
                                EstadoLinea.SIN_COMPROBAR -> {}
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !vm.ocupado && vm.resultado == null, onClick = { vm.darEntrada(f) }) { Text("Dar entrada") }
                    OutlinedButton(enabled = !vm.ocupado, onClick = { vm.comprobar(f) }) { Text("Volver a comprobar") }
                    OutlinedButton(enabled = !vm.ocupado, onClick = { vm.cerrar() }) { Text(if (vm.resultado == null) "Descartar" else "Cerrar") }
                    if (vm.ocupado) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
    vm.lineaNueva?.let { NuevoProductoDialog(vm, f, it) }
    if (vm.avisosRemarcar.isNotEmpty()) AvisoRemarcarDialog(vm.avisosRemarcar, onEntendido = { vm.avisosRemarcar = emptyList() })
}

/** Alta de un artículo desde una línea de la factura: referencia, SKU (ID del proveedor/REF), nombre, categoría (obligatoria), precio base, IVA, RE, coste, margen y PVP. Existencia 0. */
@Composable
private fun NuevoProductoDialog(vm: ImportarEscaneoViewModel, f: FacturaEscaneada, l: LineaImportada) {
    val prov = vm.proveedor
    val exento = prov?.exentoIva == true
    var ref by remember(l) { mutableStateOf(l.ref) }
    var sku by remember(l) { mutableStateOf("${prov?.id ?: ""}/${l.ref}") }
    var nombre by remember(l) { mutableStateOf(l.descripcion) }
    var categoriaId by remember(l) { mutableStateOf<Long?>(null) }
    var base by remember(l) { mutableStateOf(l.costeNeto?.let { num(it) } ?: "") }
    var iva by remember(l) { mutableStateOf(if (exento) "0" else "21") }
    var re by remember(l) { mutableStateOf(if (exento) "0" else "5,2") }
    var margen by remember(l) { mutableStateOf("") }
    var pvp by remember(l) { mutableStateOf("") }
    fun coste(): Double? = dec(base)?.let { it * (1 + (dec(iva) ?: 0.0) / 100 + (dec(re) ?: 0.0) / 100) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = { vm.lineaNueva = null },
        title = { Text("Añadir producto a Magatzem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledTextField(label = "Referencia", value = ref, onValueChange = { ref = it; sku = "${prov?.id ?: ""}/$it" }, singleLine = true, modifier = Modifier.weight(1f))
                    LabeledTextField(label = "SKU (ID/REF)", value = sku, onValueChange = { sku = it }, singleLine = true, modifier = Modifier.weight(1f))
                }
                LabeledTextField(label = "Nombre", value = nombre, onValueChange = { nombre = it }, singleLine = true)
                SelectorDropdown(label = "Categoría *", opciones = listOf<Pair<Long?, String>>(null to "—") + vm.categorias.map { it.id as Long? to it.nombre }, seleccionado = categoriaId, onSeleccionar = { categoriaId = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledTextField(label = "Precio base", value = base, onValueChange = { base = it }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f))
                    LabeledTextField(label = "% IVA", value = iva, onValueChange = { iva = it }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f))
                    LabeledTextField(label = "% RE", value = re, onValueChange = { re = it }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f))
                }
                Text("Coste (base + IVA + RE): ${coste()?.let { num(it) } ?: "—"} €", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledTextField(label = "Margen %", value = margen, onValueChange = { margen = it; val c = coste(); val m = dec(it); pvp = if (c != null && m != null) num(Math.round(c * (1 + m / 100) * 100) / 100.0) else "" },
                        singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f))
                    LabeledTextField(label = "PVP", value = pvp, onValueChange = { pvp = it; val c = coste(); val v = dec(it); margen = if (c != null && c > 0 && v != null) num(Math.round((v / c - 1) * 10000) / 100.0) else "" },
                        singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f))
                }
                Text("La existencia empieza en 0: las unidades entran al dar entrada a la factura. El margen y el PVP se pueden dejar en blanco.", style = MaterialTheme.typography.bodySmall)
                vm.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = nombre.isNotBlank() && ref.isNotBlank() && categoriaId != null && (dec(base) ?: 0.0) > 0, onClick = {
                vm.anadirProducto(f, nombre, sku, ref, categoriaId ?: return@TextButton, dec(base) ?: 0.0, dec(margen) ?: 0.0, dec(pvp) ?: 0.0)
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = { vm.lineaNueva = null }) { Text("Cancelar") } }
    )
}
