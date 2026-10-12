package com.example.magatzem.ui.documentos

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown
import java.io.File

private fun euros(v: Double?) = if (v == null) "—" else String.format(java.util.Locale.getDefault(), "%.2f €", v)

private fun archivoFoto(context: Context): Pair<File, Uri> {
    val dir = File(context.cacheDir, "fotos").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }                              // no se acumulan fotos viejas
    val f = File(dir, "factura_${System.currentTimeMillis()}.jpg")
    return f to FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
}

/**
 * Escanear factura de proveedor (Movimientos → Factura → Escanear): foto con la cámara o un PDF/imagen del aparato, lectura en el
 * propio aparato (sin internet) y una pantalla de revisión antes de crear nada. Lo reconocido son PROPUESTAS: el OCR confunde cifras.
 */
@Composable
fun EscanearFacturaDialog(
    onCerrar: () -> Unit,
    onCreada: (proveedorId: Long, docId: Long) -> Unit,
    vm: EscanearFacturaViewModel = viewModel()
) {
    val context = LocalContext.current
    val proveedores by vm.proveedores.collectAsState()
    var fotoUri by remember { mutableStateOf<Uri?>(null) }
    var avisosFinales by remember { mutableStateOf<Pair<List<AvisoRemarcar>, Pair<Long, Long>>?>(null) }
    var verTexto by remember { mutableStateOf(false) }

    val hacerFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) fotoUri?.let { vm.leer(it) } }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.leer(uri) }

    avisosFinales?.let { (avisos, ids) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Factura creada") },
            text = {
                Column {
                    Text("La factura se ha creado con sus líneas. Revísala en la pantalla siguiente.")
                    if (avisos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Hay que remarcar (el PVP ha cambiado y había unidades con el precio antiguo):", color = MaterialTheme.colorScheme.error)
                        avisos.forEach { Text("• ${it.sku ?: it.nombre}: ${it.unidadesAnteriores} ud. · PVP %.2f → %.2f €".format(it.pvpAntes, it.pvpNuevo)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { avisosFinales = null; onCreada(ids.first, ids.second); onCerrar() }) { Text("Abrir factura") } }
        )
    }

    Dialog(onDismissRequest = { if (!vm.trabajando && vm.fase != FaseEscaner.LEYENDO) { vm.reiniciar(); onCerrar() } }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Escanear factura", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                when (vm.fase) {
                    FaseEscaner.INICIO -> {
                        Text(
                            "Haz una foto de la factura (hoja plana, con buena luz y la cámara quieta) o elige un PDF o una imagen. La lectura se hace " +
                                "en este aparato, sin internet. Después podrás revisar y corregir todo antes de crear la factura.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        vm.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { val (_, uri) = archivoFoto(context); fotoUri = uri; hacerFoto.launch(uri) }) { Text("Hacer foto") }
                            OutlinedButton(onClick = { elegir.launch(arrayOf("image/*", "application/pdf")) }) { Text("Elegir foto o PDF") }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { vm.reiniciar(); onCerrar() }) { Text("Cancelar (no se guarda nada)") }
                    }
                    FaseEscaner.LEYENDO -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Leyendo la factura…")
                        }
                    }
                    FaseEscaner.REVISAR -> Revision(vm, proveedores.map { it.id to it.nombre }, verTexto, { verTexto = it },
                        onOtra = { vm.reiniciar() }, onCancelar = { vm.reiniciar(); onCerrar() },
                        onCrear = { vm.crear { prov, doc, avisos -> avisosFinales = avisos to (prov to doc) } })
                }
            }
        }
    }
}

@Composable
private fun Revision(
    vm: EscanearFacturaViewModel, proveedores: List<Pair<Long, String>>, verTexto: Boolean, onVerTexto: (Boolean) -> Unit,
    onOtra: () -> Unit, onCancelar: () -> Unit, onCrear: () -> Unit
) {
    val suma = vm.lineas.filter { it.incluida }.sumOf { it.subtotal }
    androidx.compose.runtime.LaunchedEffect(vm.proveedorId, vm.numero) { vm.comprobarExistente() }
    val ya = vm.existente
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Todavía no se ha guardado nada: puedes cancelar sin cambiar ningún dato.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (ya != null) {
            Text(
                "Esta factura YA está en Magatzem (${ya.lineas.size} líneas, ${euros(ya.total)} de coste). No se puede crear otra igual; " +
                    "te sirve para comprobar si la lectura coincide con lo que ya tienes. Cancela cuando termines.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectorDropdown(
                label = "Proveedor" + if (vm.proveedorId == null) " (no reconocido: elígelo)" else "",
                opciones = proveedores.map { it.first as Long? to it.second },
                seleccionado = vm.proveedorId, onSeleccionar = { vm.proveedorId = it }, modifier = Modifier.widthIn(max = 320.dp)
            )
            LabeledTextField(label = "Nº de factura", value = vm.numero, onValueChange = { vm.numero = it }, singleLine = true, modifier = Modifier.width(220.dp))
            LabeledTextField(label = "Fecha (dd-MM-aaaa)", value = vm.fecha, onValueChange = { vm.fecha = it }, singleLine = true, modifier = Modifier.width(170.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Leído en la factura: base ${euros(vm.leidoBase)} · IVA ${euros(vm.leidoIva)} · total ${euros(vm.leidoTotal)}.   " +
                "Suma de las líneas marcadas (sin IVA): ${euros(suma)}",
            style = MaterialTheme.typography.bodyMedium
        )
        val base = vm.leidoBase
        if (base != null && Math.abs(base - suma) > maxOf(0.05, 0.01 * base)) {
            Text("⚠ La suma de las líneas no coincide con la base de la factura: puede faltar alguna línea o haber una cifra mal leída.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        vm.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                Text("Líneas reconocidas (${vm.lineas.size}) — desmarca las que no quieras y corrige lo que haga falta", style = MaterialTheme.typography.titleSmall)
                if (vm.lineas.isEmpty()) Text("No se ha reconocido ninguna línea: las líneas se reconocen por la REF del proveedor. Créala vacía y añade las líneas a mano.", style = MaterialTheme.typography.bodyMedium)
            }
            items(vm.lineas) { l ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = l.incluida, onCheckedChange = { l.incluida = it })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(listOfNotNull(l.producto.sku, l.producto.nombre).joinToString(" · "), fontWeight = FontWeight.Medium)
                        val enMagatzem = ya?.lineas?.get(l.producto.id)
                        val comparacion = when {
                            ya == null -> ""
                            enMagatzem == null -> "no está en la factura de Magatzem · "
                            enMagatzem.first == (l.cantidad.trim().toIntOrNull() ?: -1) && Math.abs(enMagatzem.second - (l.coste.trim().replace(',', '.').toDoubleOrNull() ?: -1.0)) < 0.005 -> "✓ igual que en Magatzem · "
                            else -> "≠ en Magatzem: ${enMagatzem.first} × ${enMagatzem.second} · "
                        }
                        Text(
                            comparacion + (if (l.cuadra) "" else "⚠ cantidad × precio no cuadra con el importe: revisar · ") + "leído: ${l.texto}",
                            style = MaterialTheme.typography.bodySmall, color = if (l.cuadra) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                    LabeledTextField(label = "Cant.", value = l.cantidad, onValueChange = { l.cantidad = it.filter(Char::isDigit) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(90.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    LabeledTextField(label = "Coste", value = l.coste, onValueChange = { l.coste = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(110.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(euros(l.subtotal), modifier = Modifier.width(90.dp))
                }
                HorizontalDivider()
            }
            if (vm.sinReconocer.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Filas con importes que no se han podido asociar a un artículo (${vm.sinReconocer.size}) — añádelas a mano en la factura", style = MaterialTheme.typography.titleSmall)
                }
                items(vm.sinReconocer) { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            item {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = { onVerTexto(!verTexto) }) { Text(if (verTexto) "Ocultar el texto reconocido" else "Ver el texto reconocido") }
                if (verTexto) Text(vm.textoCompleto, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onCancelar) { Text("Cancelar (no se guarda nada)") }
            OutlinedButton(onClick = onOtra) { Text("Otra foto") }
            Spacer(modifier = Modifier.weight(1f))
            if (vm.trabajando) CircularProgressIndicator(modifier = Modifier.height(28.dp))
            Button(onClick = onCrear, enabled = !vm.trabajando && ya == null) { Text(if (ya == null) "Crear factura" else "Ya existe") }
        }
    }
}
