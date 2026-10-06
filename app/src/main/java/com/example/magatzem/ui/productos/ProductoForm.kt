package com.example.magatzem.ui.productos

import com.example.magatzem.ui.common.SelectorCategoria
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.factorCoste
import com.example.magatzem.data.factorIva
import com.example.magatzem.data.factorRecargo
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.ui.common.mostrarTecladoEnPantalla
import com.example.magatzem.ui.common.pareceCodigoBarras
import java.util.Locale
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown

private fun Double.formateado(): String = String.format(Locale.US, "%.2f", this)

@Composable
private fun CompactField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: ((String) -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        OutlinedTextField(
            value = value,
            onValueChange = { nuevo -> onValueChange?.invoke(nuevo) },
            enabled = onValueChange != null,
            readOnly = onValueChange == null,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth().mostrarTecladoEnPantalla()
        )
    }
}

/**
 * Formulario de artículo, común a cualquier alta (por ahora solo lo usa "Existencia", ver
 * [com.example.magatzem.ui.existencia.ExistenciaScreen]). Coste/IVA/R.E./Margen/PVP se recalculan
 * en vivo igual que en BackShop (ver LOGICA_COSTES_ENTRADAS.md de ese proyecto):
 * - Al teclear Coste: se recalculan IVA, R.E. y PVP (con el margen que hubiera, 0 si no hay).
 * - Al teclear PVP: se recalcula el Margen, pero solo si Coste > 0 (si no, no hay sobre qué
 *   calcular un margen, así que se deja tal cual y el PVP tecleado manda sin más).
 * Mientras no se conozca el coste (habitual en "Existencia": se sabe el PVP de sobra, el coste se
 * irá completando artículo a artículo o nunca), Coste/IVA/R.E./Margen se quedan en 0 y el PVP es
 * el que se teclea directamente.
 */
@Composable
fun ProductoForm(
    titulo: String,
    inicial: ProductoEntity?,
    categorias: List<CategoriaEntity>,
    proveedores: List<ProveedorEntity>,
    error: String?,
    onCancelar: (() -> Unit)? = null,
    textoCancelar: String = "Salir",
    /** Entradas lo cambia a "Cantidad recibida": ahí la existencia tecleada es lo que llega en esa entrada. */
    labelExistencia: String = "Existencia",
    /** Solo en edición (nunca en alta, donde el artículo todavía no existe): borra el artículo. */
    onEliminar: (() -> Unit)? = null,
    /**
     * Se llama al terminar de escanear/teclear el código de barras, el SKU o la REF (Intro, como
     * remata un lector de códigos), para avisar de un artículo duplicado antes de rellenar el
     * resto del formulario, no solo al pulsar Guardar.
     */
    onComprobarDuplicado: ((sku: String, codigoBarras: String, referenciaFabricante: String) -> Unit)? = null,
    onGuardar: (
        sku: String,
        codigoBarras: String,
        referenciaFabricante: String,
        nombre: String,
        categoriaId: Long?,
        proveedorId: Long?,
        coste: String,
        margen: String,
        precioVenta: String,
        existencia: String,
        minimo: String,
        maximo: String,
        mostrarEnTeclado: Boolean
    ) -> Unit
) {
    var skuEditadoManualmente by rememberSaveable { mutableStateOf(inicial?.sku?.isNotBlank() == true) }
    var sku by rememberSaveable { mutableStateOf(inicial?.sku.orEmpty()) }
    var codigoBarras by rememberSaveable { mutableStateOf(inicial?.codigoBarras.orEmpty()) }
    var referenciaFabricante by rememberSaveable { mutableStateOf(inicial?.referenciaFabricante.orEmpty()) }
    var nombre by rememberSaveable { mutableStateOf(inicial?.nombre.orEmpty()) }
    var categoriaId by rememberSaveable { mutableStateOf(inicial?.categoriaId) }
    var proveedorId by rememberSaveable { mutableStateOf(inicial?.proveedorId) }
    // En blanco en vez de "0"/"0.0": si no, al escribir el primer dígito se queda detrás de ese
    // "0" en vez de reemplazarlo.
    var coste by rememberSaveable { mutableStateOf(inicial?.coste?.takeIf { it != 0.0 }?.toString() ?: "") }
    var margen by rememberSaveable { mutableStateOf(inicial?.margenBeneficio?.takeIf { it != 0.0 }?.formateado() ?: "") }
    var precioVenta by rememberSaveable {
        mutableStateOf(inicial?.precioVenta?.takeIf { it != 0.0 }?.formateado() ?: "")
    }
    var existencia by rememberSaveable { mutableStateOf(inicial?.existencia?.takeIf { it != 0 }?.toString() ?: "") }
    var minimo by rememberSaveable { mutableStateOf(inicial?.minimo?.takeIf { it != 0 }?.toString() ?: "") }
    var maximo by rememberSaveable { mutableStateOf(inicial?.maximo?.toString() ?: "") }
    // Alta (o plantilla aún sin guardar): los artículos con código de barras no salen en las teclas
    // de Ventas, salvo que se marque a mano. Un artículo ya guardado conserva lo que tenía.
    val esNuevo = inicial == null || inicial.id == 0L
    var mostrarEnTeclado by rememberSaveable {
        mutableStateOf(if (esNuevo) inicial?.codigoBarras.isNullOrBlank() else inicial!!.mostrarEnTeclado)
    }
    var tecladoTocado by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(codigoBarras) {
        if (esNuevo && !tecladoTocado) mostrarEnTeclado = codigoBarras.isBlank()
    }

    fun recalcularSku(nuevaRef: String = referenciaFabricante, nuevoProveedorId: Long? = proveedorId) {
        if (skuEditadoManualmente) return
        sku = if (nuevoProveedorId != null && nuevaRef.isNotBlank()) "$nuevoProveedorId/$nuevaRef" else ""
    }

    // Proveedor sin IVA ni recargo (sellos de Correos): coste y PVP son la base.
    val exento = proveedores.firstOrNull { it.id == proveedorId }?.exentoIva == true

    fun recalcularPrecioVenta(nuevoCoste: String, nuevoMargen: String) {
        val c = nuevoCoste.replace(',', '.').toDoubleOrNull() ?: 0.0
        val m = nuevoMargen.replace(',', '.').toDoubleOrNull() ?: 0.0
        precioVenta = ProductoEntity.calcularPrecioVenta(c, m, exento).takeIf { it != 0.0 }?.formateado() ?: ""
    }

    // Inversa de calcularPrecioVenta: PVP = coste*IVA_MAS_RECARGO*(1+margen/100)
    // => margen = ((PVP / (coste*IVA_MAS_RECARGO)) - 1) * 100. Solo tiene sentido con coste > 0.
    fun recalcularMargen(nuevoCoste: String, nuevoPrecioVenta: String) {
        val c = nuevoCoste.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (c <= 0) return
        val p = nuevoPrecioVenta.replace(',', '.').toDoubleOrNull() ?: 0.0
        margen = (((p / (c * factorCoste(exento))) - 1) * 100).formateado()
    }

    val importeIva = remember(coste, exento) {
        val c = coste.replace(',', '.').toDoubleOrNull() ?: 0.0
        (c * (factorIva(exento) - 1)).formateado()
    }
    val importeRecargo = remember(coste, exento) {
        val c = coste.replace(',', '.').toDoubleOrNull() ?: 0.0
        (c * factorRecargo(exento)).formateado()
    }
    // Coste + IVA + recargo de equivalencia: es la base sobre la que se aplica el margen.
    val costeTotal = remember(coste, exento) {
        val c = coste.replace(',', '.').toDoubleOrNull() ?: 0.0
        (c * factorCoste(exento)).formateado()
    }

    // Ninguno de los dos es obligatorio: sin elegir nada, se muestran como "—" (no hay opción
    // explícita "sin categoría/proveedor" en la lista, simplemente no se selecciona ninguna).
    val proveedorOpciones = remember(proveedores) {
        proveedores.map<ProveedorEntity, Pair<Long?, String>> { it.id to it.nombre }
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .widthIn(max = 680.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = titulo, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            LabeledTextField(
                label = "Código de barras",
                value = codigoBarras,
                onValueChange = { codigoBarras = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onComprobarDuplicado?.invoke(sku, codigoBarras, referenciaFabricante)
                }),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            LabeledTextField(
                label = "SKU",
                value = sku,
                onValueChange = {
                    sku = it
                    skuEditadoManualmente = true
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (pareceCodigoBarras(sku)) {
                        codigoBarras = sku.trim()
                        sku = ""
                        skuEditadoManualmente = false
                        recalcularSku()
                    }
                    onComprobarDuplicado?.invoke(sku, codigoBarras, referenciaFabricante)
                }),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            LabeledTextField(
                label = "REF (fabricante)",
                value = referenciaFabricante,
                onValueChange = {
                    referenciaFabricante = it
                    recalcularSku(nuevaRef = it)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (pareceCodigoBarras(referenciaFabricante)) {
                        codigoBarras = referenciaFabricante.trim()
                        referenciaFabricante = ""
                        recalcularSku(nuevaRef = "")
                    }
                    onComprobarDuplicado?.invoke(sku, codigoBarras, referenciaFabricante)
                }),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LabeledTextField(
            label = "Nombre",
            value = nombre,
            onValueChange = { nombre = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            SelectorCategoria(
                label = "Categoría *",
                categorias = categorias,
                seleccionado = categoriaId,
                onSeleccionar = { categoriaId = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            SelectorDropdown(
                label = "Proveedor *",
                opciones = proveedorOpciones,
                seleccionado = proveedorId,
                onSeleccionar = {
                    proveedorId = it
                    recalcularSku(nuevoProveedorId = it)
                    // Al cambiar a/de un proveedor exento, el PVP sale de otro coste total.
                    val exentoNuevo = proveedores.firstOrNull { p -> p.id == it }?.exentoIva == true
                    val c = coste.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val m = margen.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (c > 0) precioVenta = ProductoEntity.calcularPrecioVenta(c, m, exentoNuevo).formateado()
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            CompactField(
                label = "Base",
                value = coste,
                onValueChange = {
                    coste = it
                    recalcularPrecioVenta(it, margen)
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            CompactField(
                label = "IVA",
                value = importeIva,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            CompactField(
                label = "R.E.",
                value = importeRecargo,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            CompactField(
                label = "Coste",
                value = costeTotal,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            CompactField(
                label = "Margen",
                value = margen,
                onValueChange = {
                    margen = it
                    recalcularPrecioVenta(coste, it)
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            CompactField(
                label = "PVP",
                value = precioVenta,
                onValueChange = {
                    precioVenta = it
                    recalcularMargen(coste, it)
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            LabeledTextField(
                label = labelExistencia,
                value = existencia,
                onValueChange = { existencia = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                compacto = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            LabeledTextField(
                label = "Mínimo",
                value = minimo,
                onValueChange = { minimo = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                compacto = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            LabeledTextField(
                label = "Máximo",
                value = maximo,
                onValueChange = { maximo = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                compacto = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp).clickable {
                mostrarEnTeclado = !mostrarEnTeclado
                tecladoTocado = true
            }
        ) {
            Checkbox(checked = mostrarEnTeclado, onCheckedChange = {
                mostrarEnTeclado = it
                tecladoTocado = true
            })
            Text("Mostrar en teclado")
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                onGuardar(
                    sku, codigoBarras, referenciaFabricante, nombre, categoriaId, proveedorId,
                    coste, margen, precioVenta, existencia, minimo, maximo, mostrarEnTeclado
                )
            }) {
                Text("Guardar")
            }
            if (onCancelar != null) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onCancelar) {
                    Text(textoCancelar)
                }
            }
            if (onEliminar != null) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onEliminar) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
