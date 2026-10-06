package com.example.magatzem.ui.common

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView

/**
 * Un lector de código de barras puede ir conectado como teclado físico (USB/Bluetooth). Cuando
 * Android detecta un teclado físico, al enfocar un campo de texto no muestra el teclado en pantalla:
 * solo una franja estrecha para elegir teclado/emojis/menú, pensada para quien vaya a escribir con
 * ese teclado físico. Aquí se necesita poder escribir a mano igualmente, así que se fuerza el
 * teclado en pantalla al enfocar el campo (`SHOW_FORCED` ignora esa detección).
 */
fun Modifier.mostrarTecladoEnPantalla(): Modifier = composed {
    val view = LocalView.current
    val imm = remember(view) {
        view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    }
    onFocusChanged { estado ->
        if (estado.isFocused) {
            imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }
    }
}
