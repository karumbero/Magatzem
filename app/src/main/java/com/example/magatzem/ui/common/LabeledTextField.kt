package com.example.magatzem.ui.common

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.remember
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Campo de texto con la etiqueta siempre visible encima (no flotante), para que un campo
 * vacío y uno con contenido se vean igual de "etiquetados" en vez de que el vacío parezca
 * sin etiqueta.
 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    compacto: Boolean = false,
    /** Campo un 20 % menos alto que el normal (45 dp en vez de 56); para formularios muy largos. */
    bajo: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    campoModifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = if (compacto) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
        )
        if (bajo) {
            val interaction = remember { MutableInteractionSource() }
            val estilo = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                textStyle = estilo,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp).then(campoModifier).mostrarTecladoEnPantalla(),
                decorationBox = { campo ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value,
                        innerTextField = campo,
                        enabled = true,
                        singleLine = singleLine,
                        visualTransformation = visualTransformation,
                        interactionSource = interaction,
                        isError = isError,
                        supportingText = supportingText,
                        contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 0.dp, bottom = 0.dp),
                        container = {
                            OutlinedTextFieldDefaults.Container(
                                enabled = true,
                                isError = isError,
                                interactionSource = interaction
                            )
                        }
                    )
                }
            )
            return@Column
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            supportingText = supportingText,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            textStyle = if (compacto) MaterialTheme.typography.bodyMedium else LocalTextStyle.current,
            modifier = Modifier.fillMaxWidth().then(campoModifier).mostrarTecladoEnPantalla()
        )
    }
}
