package com.example.magatzem.ui.common

/**
 * Convierte una fecha guardada en formato ISO ("yyyy-MM-dd" o "yyyy-MM-dd HH:mm:ss") al formato de
 * visualización dd-MM-yyyy (conservando la hora si la fecha original la incluye). El almacenamiento
 * interno se mantiene en ISO a propósito, porque el orden alfabético de ese formato coincide con el
 * orden cronológico (se usa en ORDER BY fecha de las consultas); esta función solo cambia cómo se
 * le muestra la fecha al usuario, nunca lo que se guarda.
 */
fun formatearFecha(fechaIso: String): String {
    if (fechaIso.length < 10) return fechaIso
    val partes = fechaIso.substring(0, 10).split("-")
    if (partes.size != 3) return fechaIso
    val (anio, mes, dia) = partes
    return "$dia-$mes-$anio${fechaIso.substring(10)}"
}

/** Fecha de hoy en dd-MM-yyyy, el formato en el que se teclean y muestran las fechas de documentos. */
fun hoyFecha(): String = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())

private val FECHA_DOC = Regex("""\d{2}-\d{2}-\d{4}""")

/** ¿Es una fecha dd-MM-yyyy real (no 31-02-2026)? Así se teclean las fechas de documentos. */
fun esFechaValida(texto: String): Boolean {
    if (!FECHA_DOC.matches(texto)) return false
    val formato = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).apply { isLenient = false }
    return runCatching { formato.parse(texto) }.getOrNull() != null
}

/** Clave para ordenar fechas de documentos (dd-MM-yyyy → yyyy-MM-dd); lo que no encaje se deja tal cual. */
fun claveOrdenFecha(texto: String): String =
    if (FECHA_DOC.matches(texto)) "${texto.substring(6)}-${texto.substring(3, 5)}-${texto.substring(0, 2)}" else texto

/**
 * Fecha de documento tal como se guarda: ISO yyyy-MM-dd (ordenable y agrupable por mes con substr). Acepta lo
 * tecleado en dd-MM-yyyy; si ya viene en ISO la deja tal cual.
 */
fun fechaIso(texto: String): String = claveOrdenFecha(texto.trim()).take(10)

/** Fecha de albarán/factura para mostrar: ya se teclea como dd-MM-yyyy (se deja tal cual); lo guardado en ISO se convierte. */
fun fechaDocumento(texto: String): String = if (FECHA_DOC.matches(texto)) texto else formatearFecha(texto)

/** Fecha de hoy + [dias] días, en dd-MM-yyyy. */
fun fechaMasDias(dias: Int): String {
    val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, dias) }
    return java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(cal.time)
}
