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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.magatzem.ui.common.LabeledTextField

/** Se muestra tras un PIN por defecto correcto; obliga a fijar uno nuevo antes de dejar entrar. */
@Composable
fun CambiarPinDialog(viewModel: SesionViewModel) {
    var nuevoPin by rememberSaveable { mutableStateOf("") }
    var confirmarPin by rememberSaveable { mutableStateOf("") }

    fun intentarCambiar() {
        viewModel.cambiarPin(nuevoPin, confirmarPin)
    }

    AlertDialog(
        onDismissRequest = { /* no se puede cerrar sin fijar un PIN nuevo */ },
        title = { Text("Cambia tu PIN") },
        text = {
            Column(modifier = Modifier.width(220.dp)) {
                Text("Es tu primer acceso. Elige un PIN nuevo de 6 dígitos.")
                Spacer(modifier = Modifier.height(8.dp))
                LabeledTextField(
                    label = "Nuevo PIN",
                    value = nuevoPin,
                    onValueChange = {
                        if (it.length <= 6) nuevoPin = it.filter(Char::isDigit)
                        viewModel.limpiarError()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                )
                Spacer(modifier = Modifier.height(8.dp))
                LabeledTextField(
                    label = "Repite el PIN",
                    value = confirmarPin,
                    onValueChange = {
                        if (it.length <= 6) confirmarPin = it.filter(Char::isDigit)
                        viewModel.limpiarError()
                    },
                    isError = viewModel.error != null,
                    supportingText = { viewModel.error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { intentarCambiar() }),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { intentarCambiar() }) { Text("Guardar") }
        }
    )
}
