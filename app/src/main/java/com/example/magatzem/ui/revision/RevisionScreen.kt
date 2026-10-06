package com.example.magatzem.ui.revision

import android.app.Application
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.data.factorCoste
import com.example.magatzem.ui.common.SelectorDropdown
import com.example.magatzem.ui.documentos.AvisoRemarcarDialog
import com.example.magatzem.ui.productos.ProductoForm
import com.example.magatzem.ui.productos.ProductoViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Un tipo de fallo que se busca en los artículos (Revisión de datos). */
enum class Problema(val titulo: String) {
    SIN_SKU("Sin SKU"),
    SIN_REF("Sin REF"),
    SIN_EAN("Sin código de barras (EAN)"),
    SIN_NOMBRE("Sin nombre"),
    SIN_EXISTENCIA("Sin existencia (0)"),
    EXISTENCIA_NEGATIVA("Existencia negativa"),
    SIN_COSTE("Sin precio de coste"),
    SIN_PVP("Sin PVP"),
    SIN_MARGEN("Sin margen"),
    PVP_BAJO_COSTE("PVP por debajo del coste (con IVA y recargo)"),
    SIN_CATEGORIA("Sin categoría"),
    SIN_PROVEEDOR("Sin proveedor"),
    BAJO_MINIMO("Existencia por debajo del mínimo"),
    SIN_MAXIMO("Sin máximo"),
    SKU_NO_COINCIDE("SKU no coincide con proveedor/REF"),
    NOMBRE_REPETIDO("Nombre repetido")
}

/** Qué fallos tiene un artículo (puede tener varios). */
private fun problemasDe(p: ProductoEntity, exento: Boolean, nombresRepetidos: Set<String>): Set<Problema> = buildSet {
    if (p.sku.isNullOrBlank()) add(Problema.SIN_SKU)
    if (p.referenciaFabricante.isNullOrBlank()) add(Problema.SIN_REF)
    if (p.codigoBarras.isNullOrBlank()) add(Problema.SIN_EAN)
    if (p.nombre.isBlank()) add(Problema.SIN_NOMBRE)
    if (p.existencia == 0) add(Problema.SIN_EXISTENCIA)
    if (p.existencia < 0) add(Problema.EXISTENCIA_NEGATIVA)
    if (p.coste <= 0.0) add(Problema.SIN_COSTE)
    if (p.precioVenta <= 0.0) add(Problema.SIN_PVP)
    if (p.margenBeneficio == 0.0 && p.coste > 0.0) add(Problema.SIN_MARGEN)
    if (p.coste > 0.0 && p.precioVenta > 0.0 && p.precioVenta < p.coste * factorCoste(exento)) add(Problema.PVP_BAJO_COSTE)
    if (p.categoriaId == null) add(Problema.SIN_CATEGORIA)
    if (p.proveedorId == null) add(Problema.SIN_PROVEEDOR)
    if (p.minimo > 0 && p.existencia < p.minimo) add(Problema.BAJO_MINIMO)
    if (p.maximo == null) add(Problema.SIN_MAXIMO)
    // El SKU sigue el formato "proveedor/REF" cuando lo genera la app; si no cuadra con el proveedor o con la REF, suele ser un error.
    if (!p.sku.isNullOrBlank() && !p.referenciaFabricante.isNullOrBlank() && p.proveedorId != null &&
        p.sku != "${p.proveedorId}/${p.referenciaFabricante}"
    ) add(Problema.SKU_NO_COINCIDE)
    if (p.nombre.isNotBlank() && p.nombre.trim().lowercase() in nombresRepetidos) add(Problema.NOMBRE_REPETIDO)
}

class RevisionViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    val productos: StateFlow<List<ProductoEntity>> = db.productoDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val proveedores: StateFlow<List<ProveedorEntity>> = db.proveedorDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categorias: StateFlow<List<CategoriaEntity>> = db.categoriaDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/**
 * Revisión de datos (icono del bicho): lista los artículos con algún fallo — sin SKU, REF, código de barras, nombre,
 * existencia, coste, PVP… — filtrando por tipo de fallo, proveedor y categoría. Al pulsar un artículo se abre para corregirlo.
 */
@Composable
fun RevisionScreen(viewModel: RevisionViewModel = viewModel(), productoViewModel: ProductoViewModel = viewModel()) {
    val productos by viewModel.productos.collectAsState()
    val proveedores by viewModel.proveedores.collectAsState()
    val categorias by viewModel.categorias.collectAsState()

    var problemaId by rememberSaveable { mutableStateOf<Long?>(null) } // null = cualquier problema
    var proveedorId by rememberSaveable { mutableStateOf<Long?>(null) } // null = todos
    var categoriaId by rememberSaveable { mutableStateOf<Long?>(null) } // null = todas
    var editando by remember { mutableStateOf<ProductoEntity?>(null) }

    val exentos = remember(proveedores) { proveedores.filter { it.exentoIva }.map { it.id }.toSet() }
    val repetidos = remember(productos) {
        productos.map { it.nombre.trim().lowercase() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys
    }
    // Primero se aplican proveedor y categoría; sobre ese conjunto se cuentan los fallos de cada tipo.
    val filtrados = remember(productos, proveedorId, categoriaId) {
        productos.filter { (proveedorId == null || it.proveedorId == proveedorId) && (categoriaId == null || it.categoriaId == categoriaId) }
    }
    val conProblemas = remember(filtrados, exentos, repetidos) {
        filtrados.map { it to problemasDe(it, it.proveedorId in exentos, repetidos) }
    }
    val cuentas = remember(conProblemas) { Problema.entries.associateWith { p -> conProblemas.count { p in it.second } } }
    val problema = problemaId?.let { Problema.entries.getOrNull(it.toInt()) }
    val lista = remember(conProblemas, problema) {
        conProblemas.filter { (_, ps) -> if (problema == null) ps.isNotEmpty() else problema in ps }
            .sortedBy { (it, _) -> (it.sku ?: it.nombre).lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Revisión de datos", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SelectorDropdown(
                label = "Problema",
                opciones = listOf<Pair<Long?, String>>(null to "Cualquier problema (${conProblemas.count { it.second.isNotEmpty() }})") +
                    Problema.entries.mapIndexed { i, p -> i.toLong() as Long? to "${p.titulo} (${cuentas[p] ?: 0})" },
                seleccionado = problemaId,
                onSeleccionar = { problemaId = it },
                modifier = Modifier.weight(1.4f)
            )
            SelectorDropdown(
                label = "Proveedor",
                opciones = listOf<Pair<Long?, String>>(null to "Todos") + proveedores.map { it.id as Long? to it.nombre },
                seleccionado = proveedorId,
                onSeleccionar = { proveedorId = it },
                modifier = Modifier.weight(1f)
            )
            SelectorDropdown(
                label = "Categoría",
                opciones = listOf<Pair<Long?, String>>(null to "Todas") + categorias.map { it.id as Long? to it.nombre },
                seleccionado = categoriaId,
                onSeleccionar = { categoriaId = it },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "${lista.size} artículos de ${filtrados.size}" + if (lista.isEmpty()) " — nada que revisar" else "",
            style = MaterialTheme.typography.titleSmall
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        val proveedorPorId = remember(proveedores) { proveedores.associateBy { it.id } }
        val categoriaPorId = remember(categorias) { categorias.associateBy { it.id } }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(lista, key = { it.first.id }) { (p, ps) ->
                Column(modifier = Modifier.fillMaxWidth().clickable { editando = p }.padding(vertical = 8.dp)) {
                    Text(
                        listOf(p.sku ?: "(sin SKU)", p.nombre.ifBlank { "(sin nombre)" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "REF ${p.referenciaFabricante ?: "—"} · EAN ${p.codigoBarras ?: "—"} · exist. ${p.existencia} · " +
                            "coste %.2f · PVP %.2f".format(p.coste, p.precioVenta) +
                            " · ${proveedorPorId[p.proveedorId]?.nombre ?: "sin proveedor"} · ${categoriaPorId[p.categoriaId]?.nombre ?: "sin categoría"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        ps.joinToString(" · ") { it.titulo },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider()
            }
        }
    }

    productoViewModel.avisoRemarcar?.let { aviso ->
        AvisoRemarcarDialog(listOf(aviso), onEntendido = productoViewModel::cerrarAvisoRemarcar)
    }

    editando?.let { p ->
        Dialog(onDismissRequest = { editando = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface {
                Card(modifier = Modifier.padding(24.dp)) {
                    ProductoForm(
                        titulo = "Editar artículo",
                        inicial = p,
                        categorias = categorias,
                        proveedores = proveedores,
                        error = productoViewModel.error,
                        onCancelar = { editando = null; productoViewModel.limpiarError() }
                    ) { sku, codigoBarras, ref, nombre, cat, prov, coste, margen, pvp, existencia, minimo, maximo, teclado ->
                        productoViewModel.actualizar(p, sku, codigoBarras, ref, nombre, cat, prov, coste, margen, pvp, existencia, minimo, maximo, teclado) {
                            editando = null
                        }
                    }
                }
            }
        }
    }
}
