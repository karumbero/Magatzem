package com.example.magatzem.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.magatzem.data.CategoriaEntity

/**
 * Selector de categoría con las subcategorías **colapsadas**: la lista muestra solo las categorías de primer nivel y,
 * a la derecha de las que tienen subcategorías, una flecha para desplegarlas (como en el listado de Categorías). Tocar
 * el nombre (de una categoría o de una subcategoría) la elige. El desplegable es más ancho que el botón.
 */
@Composable
fun SelectorCategoria(
    label: String,
    categorias: List<CategoriaEntity>,
    seleccionado: Long?,
    onSeleccionar: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    compacto: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val abiertas = remember { mutableStateMapOf<Long, Boolean>() }
    val padres = remember(categorias) { categorias.filter { it.parentId == null }.sortedBy { it.nombre.lowercase() } }
    val hijasPorPadre = remember(categorias) {
        categorias.filter { it.parentId != null }.groupBy { it.parentId }
            .mapValues { (_, hijas) -> hijas.sortedBy { it.nombre.lowercase() } }
    }
    val textoSeleccionado = categorias.firstOrNull { it.id == seleccionado }?.nombre ?: "—"

    Column(modifier = modifier) {
        Text(
            text = label,
            style = if (compacto) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = if (compacto) PaddingValues(horizontal = 12.dp, vertical = 4.dp) else ButtonDefaults.ContentPadding,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = textoSeleccionado,
                    style = if (compacto) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 340.dp)
            ) {
                padres.forEach { padre ->
                    val hijas = hijasPorPadre[padre.id].orEmpty()
                    val abierta = abiertas[padre.id] ?: false
                    DropdownMenuItem(
                        text = { Text(padre.nombre) },
                        trailingIcon = if (hijas.isEmpty()) null else {
                            {
                                IconButton(onClick = { abiertas[padre.id] = !abierta }) {
                                    Icon(
                                        imageVector = if (abierta) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                        contentDescription = if (abierta) "Contraer" else "Desplegar"
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSeleccionar(padre.id)
                            expanded = false
                        }
                    )
                    if (abierta) {
                        hijas.forEach { hija ->
                            DropdownMenuItem(
                                text = { Text("— ${hija.nombre}") },
                                modifier = Modifier.widthIn(min = 340.dp),
                                onClick = {
                                    onSeleccionar(hija.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
