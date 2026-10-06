package com.example.magatzem.ui.sesion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.magatzem.ui.common.LabeledTextField
import com.example.magatzem.ui.common.SelectorDropdown

/** Bloquea la app hasta elegir usuario e introducir su PIN; no se puede cerrar de otra forma. */
@Composable
fun LoginDialog(viewModel: SesionViewModel, onCancelar: (() -> Unit)? = null) {
    val usuarios by viewModel.usuarios.collectAsState()
    var usuarioId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    val focusPin = remember { FocusRequester() }

    // Preselecciona el primer usuario dado de alta (por id, no por el orden alfabético de la
    // lista) para no dejar el desplegable vacío; si ya hay uno elegido no se toca.
    LaunchedEffect(usuarios) {
        if (usuarioId == null) {
            usuarioId = usuarios.minByOrNull { it.id }?.id
        }
    }

    // Con el usuario ya preseleccionado, solo falta teclear el PIN: el foco va directo ahí.
    LaunchedEffect(Unit) { focusPin.requestFocus() }

    fun intentarEntrar() {
        viewModel.iniciarSesion(usuarioId, pin)
        pin = ""
    }

    AlertDialog(
        onDismissRequest = { onCancelar?.invoke() },
        title = { Text("Iniciar sesión") },
        text = {
            Column(modifier = Modifier.width(220.dp)) {
                SelectorDropdown(
                    label = "Usuario",
                    opciones = usuarios.map { it.id to it.nombre },
                    seleccionado = usuarioId,
                    onSeleccionar = {
                        usuarioId = it
                        viewModel.limpiarError()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LabeledTextField(
                    label = "PIN",
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6) pin = it.filter(Char::isDigit)
                        viewModel.limpiarError()
                    },
                    isError = viewModel.error != null,
                    supportingText = { viewModel.error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { intentarEntrar() }),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier,
                    campoModifier = Modifier.focusRequester(focusPin)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { intentarEntrar() }) { Text("Aceptar") }
        },
        dismissButton = onCancelar?.let { cancelar -> { TextButton(onClick = cancelar) { Text("Cancelar") } } }
    )
}
