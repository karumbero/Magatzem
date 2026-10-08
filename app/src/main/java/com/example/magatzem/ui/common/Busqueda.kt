package com.example.magatzem.ui.common

import java.text.Normalizer

private val MARCAS = Regex("\\p{M}+")

/** Minúsculas y sin acentos ni diéresis (á→a, ü→u, ñ→n…): para comparar textos tecleados con o sin ellos. */
fun String.sinAcentos(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(MARCAS, "").lowercase()

/** true si alguno de los textos contiene `consulta` sin tener en cuenta mayúsculas, acentos ni diéresis. */
fun contieneBusqueda(consulta: String, vararg textos: String?): Boolean {
    val q = consulta.trim().sinAcentos()
    return q.isEmpty() || textos.any { it != null && it.sinAcentos().contains(q) }
}
