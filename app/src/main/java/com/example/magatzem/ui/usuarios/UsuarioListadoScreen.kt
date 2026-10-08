package com.example.magatzem.ui.usuarios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.magatzem.data.UsuarioEntity
import com.example.magatzem.ui.common.ConfirmDeleteDialog
import com.example.magatzem.ui.common.LabeledTextField

@Composable
fun UsuarioListadoScreen(viewModel: UsuarioViewModel = viewModel(), onAnadir: () -> Unit = {}) {
    val usuarios by viewModel.usuarios.collectAsState()
    var usuarioEditando by remember { mutableStateOf<UsuarioEntity?>(null) }
    var usuarioEliminando by remember { mutableStateOf<UsuarioEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Usuarios", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onAnadir) { Text("Añadir +") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(usuarios, key = { it.id }) { usuario ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(usuario.nombre)
                        }
                        append("  nivel ${usuario.nivel}  ")
                        if (!usuario.activo) append("(desactivado)  ")
                        // PIN oculto, como una contraseña: solo puntos, nunca los dígitos reales.
                        append("•".repeat(usuario.pin.length))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { usuarioEditando = usuario }
                        .padding(vertical = 12.dp)
                )
                HorizontalDivider()
            }
        }
    }

    usuarioEditando?.let { usuario ->
        EditarUsuarioDialog(
            usuario = usuario,
            viewModel = viewModel,
            onDismiss = {
                usuarioEditando = null
                viewModel.limpiarError()
            },
            onEliminar = {
                usuarioEditando = null
                usuarioEliminando = usuario
            }
        )
    }

    usuarioEliminando?.let { usuario ->
        ConfirmDeleteDialog(
            itemLabel = usuario.nombre,
            onConfirm = {
                viewModel.eliminar(usuario)
                usuarioEliminando = null
            },
            onDismiss = { usuarioEliminando = null }
        )
    }
}

@Composable
private fun EditarUsuarioDialog(
    usuario: UsuarioEntity,
    viewModel: UsuarioViewModel,
    onDismiss: () -> Unit,
    onEliminar: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf(usuario.nombre) }
    var pin by rememberSaveable { mutableStateOf(usuario.pin) }
    var nivel by rememberSaveable { mutableStateOf(usuario.nivel.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar usuario") },
        text = {
            Column {
                LabeledTextField(
                    label = "Nombre",
                    value = nombre,
                    onValueChange = { nombre = it },
                    isError = viewModel.error != null,
                    supportingText = { viewModel.error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    LabeledTextField(
                        label = "PIN (6 dígitos)",
                        value = pin,
                        onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LabeledTextField(
                        label = "Nivel (1 dígito)",
                        value = nivel,
                        onValueChange = { if (it.length <= 1) nivel = it.filter(Char::isDigit) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextButton(onClick = { viewModel.cambiarActivo(usuario) { onDismiss() } }) {
                    Text(if (usuario.activo) "Desactivar" else "Activar")
                }
                // Los de nivel 1 no se borran (se sincronizan con MiTPV): solo se desactivan.
                if (usuario.nivel != 1) {
                    TextButton(onClick = onEliminar) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.actualizar(usuario, nombre, pin, nivel) { onDismiss() }
            }) { Text("Guardar cambios") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
