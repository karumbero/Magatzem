package com.example.magatzem.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Selector desplegable simple (id nulo permitido, ej. "sin categoría"). */
@Composable
fun SelectorDropdown(
    label: String,
    opciones: List<Pair<Long?, String>>,
    seleccionado: Long?,
    onSeleccionar: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    compacto: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val textoSeleccionado = opciones.firstOrNull { it.first == seleccionado }?.second ?: "—"
    Column(modifier = modifier) {
        Text(
            text = label,
            style = if (compacto) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = if (compacto) {
                    PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                } else {
                    ButtonDefaults.ContentPadding
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = textoSeleccionado,
                    style = if (compacto) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                opciones.forEach { (id, nombre) ->
                    DropdownMenuItem(
                        text = { Text(nombre) },
                        onClick = {
                            onSeleccionar(id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
