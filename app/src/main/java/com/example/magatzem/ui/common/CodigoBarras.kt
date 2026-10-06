package com.example.magatzem.ui.common

/** Un lector de código de barras "teclea" el código y remata con Enter; si al pulsar Enter
 * lo tecleado es puramente numérico y de una longitud típica de EAN/UPC (8, 12 o 13 dígitos),
 * lo tratamos como código de barras leído en vez de texto tecleado a mano. */
fun pareceCodigoBarras(texto: String): Boolean {
    val limpio = texto.trim()
    return limpio.length in setOf(8, 12, 13) && limpio.all { it.isDigit() }
}
