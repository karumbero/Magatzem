package com.example.magatzem.ui.oficina

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.ui.common.formatearFecha
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun euros(valor: Double) = "%.2f €".format(valor)

/** Periodo con el que se filtran los movimientos de Caja y de Bancos. El saldo siempre cuenta todos los movimientos. */
private enum class Periodo(val etiqueta: String) {
    HOY("Hoy"), SEMANA("Última semana"), MES("Último mes"), TODOS("Todos");

    /** Primer día incluido ("yyyy-MM-dd"; las fechas de los movimientos son ISO, así que se comparan como texto). Null = sin límite. */
    fun desde(): String? {
        val dias = when (this) {
            HOY -> 0
            SEMANA -> -7
            MES -> 0
            TODOS -> return null
        }
        val cal = Calendar.getInstance()
        if (this == MES) cal.add(Calendar.MONTH, -1) else cal.add(Calendar.DAY_OF_YEAR, dias)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
}

private fun dentro(fecha: String, desde: String?) = desde == null || fecha >= desde

@Composable
private fun SelectorPeriodo(periodo: Periodo, onCambiar: (Periodo) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Periodo.entries.forEach { p ->
            FilterChip(selected = p == periodo, onClick = { onCambiar(p) }, label = { Text(p.etiqueta) })
        }
    }
}

@Composable
private fun CabeceraCuenta(titulo: String, saldo: Double, periodo: Periodo, delPeriodo: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text("Saldo ${euros(saldo)}", style = MaterialTheme.typography.titleMedium)
    }
    if (periodo != Periodo.TODOS) {
        Text(
            "${periodo.etiqueta}: " + (if (delPeriodo > 0) "+" else "") + euros(delPeriodo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}

@Composable
private fun FilaMovimiento(fecha: String, concepto: String, importe: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${formatearFecha(fecha)} · $concepto", style = MaterialTheme.typography.bodyMedium)
        Text(
            (if (importe > 0) "+" else "") + euros(importe),
            style = MaterialTheme.typography.bodyMedium,
            color = if (importe < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SinMovimientos(periodo: Periodo) {
    Text(
        if (periodo == Periodo.TODOS) "Sin movimientos todavía." else "Sin movimientos en este periodo.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/**
 * Oficina → Caja. El saldo es la suma de todos los movimientos del efectivo de la tienda, que genera la
 * importación de los cierres de MiTPV (ventas al contado, salidas, descuadres). El filtro (Hoy, Última
 * semana, Último mes, Todos) solo limita los movimientos que se listan; la lista se desplaza con scroll.
 */
@Composable
fun CajaScreen(viewModel: CajaBancosViewModel = viewModel()) {
    val caja by viewModel.movimientosCaja.collectAsState()
    var periodo by rememberSaveable { mutableStateOf(Periodo.TODOS) }
    val desde = periodo.desde()
    val visibles = caja.filter { dentro(it.fecha, desde) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Caja", style = MaterialTheme.typography.headlineSmall)
        SelectorPeriodo(periodo) { periodo = it }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { CabeceraCuenta("Caja (efectivo)", caja.sumOf { it.importe }, periodo, visibles.sumOf { it.importe }) }
            if (visibles.isEmpty()) item { SinMovimientos(periodo) }
            items(visibles, key = { "c${it.id}" }) { FilaMovimiento(it.fecha, it.concepto, it.importe) }
        }
    }
}

/**
 * Oficina → Bancos. Saldo y movimientos de cada cuenta bancaria (cobros con tarjeta, ingresos de caja y pagos de
 * facturas). Mismo filtro por periodo que Caja y lista con scroll.
 */
@Composable
fun BancosMovimientosScreen(viewModel: CajaBancosViewModel = viewModel()) {
    val movBanco by viewModel.movimientosBanco.collectAsState()
    val bancos by viewModel.bancos.collectAsState()
    var periodo by rememberSaveable { mutableStateOf(Periodo.TODOS) }
    val desde = periodo.desde()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Bancos", style = MaterialTheme.typography.headlineSmall)
        SelectorPeriodo(periodo) { periodo = it }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (bancos.isEmpty()) item { Text("No hay ninguna cuenta bancaria todavía.", style = MaterialTheme.typography.bodyMedium) }
            bancos.forEach { banco ->
                val propios = movBanco.filter { it.bancoId == banco.id }
                val visibles = propios.filter { dentro(it.fecha, desde) }
                item(key = "bh${banco.id}") { CabeceraCuenta(banco.nombre, propios.sumOf { it.importe }, periodo, visibles.sumOf { it.importe }) }
                if (visibles.isEmpty()) item(key = "be${banco.id}") { SinMovimientos(periodo) }
                items(visibles, key = { "b${it.id}" }) { FilaMovimiento(it.fecha, it.concepto, it.importe) }
            }
        }
    }
}
