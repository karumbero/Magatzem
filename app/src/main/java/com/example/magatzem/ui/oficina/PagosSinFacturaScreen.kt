package com.example.magatzem.ui.oficina

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.ui.common.formatearFecha

/**
 * Oficina → Pagos sin factura: pagos a proveedor en efectivo hechos desde MiTPV cuya factura todavía no
 * está en Magatzem (o cuyo proveedor/número no coincide). En cuanto se crea una factura con ese proveedor
 * y número, el pago se aplica solo y desaparece de esta lista.
 */
@Composable
fun PagosSinFacturaScreen() {
    val db = (LocalContext.current.applicationContext as MagatzemApplication).database
    val pagos by remember { db.cierreImportadoDao().observeRetiradasProveedorSinAplicar() }.collectAsState(initial = emptyList())
    val proveedores by remember { db.proveedorDao().observeAll() }.collectAsState(initial = emptyList())
    val nombre = remember(proveedores) { proveedores.associate { it.uuid to it.nombre } }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Pagos sin factura", style = MaterialTheme.typography.headlineSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        if (pagos.isEmpty()) {
            Text("No hay pagos de MiTPV esperando factura.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Pagos en efectivo hechos desde MiTPV: se aplicarán solos al crear la factura de ese proveedor con ese número.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pagos, key = { it.id }) { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        androidx.compose.foundation.layout.Column {
                            Text(
                                p.proveedorUuid?.let { nombre[it] } ?: "Sin proveedor indicado",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "${formatearFecha(p.fecha)} · factura ${p.numeroFactura ?: "—"}" + (p.nota?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("%.2f €".format(p.importe), style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
