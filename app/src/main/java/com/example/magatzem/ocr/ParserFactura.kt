package com.example.magatzem.ocr

import java.text.Normalizer

/** Proveedor y artículo reducidos a lo que necesita el intérprete (no depende de Room, para poder probarlo sin aparato). */
data class ProvInfo(val id: Long, val nombre: String, val nif: String?)
data class ProdInfo(val id: Long, val sku: String?, val ref: String?, val nombre: String, val proveedorId: Long?)

/** Una línea de la factura reconocida: el artículo, la cantidad y el precio de coste que mejor cuadran con el importe leído. */
data class LineaLeida(
    val producto: ProdInfo, val cantidad: Int, val coste: Double, val importe: Double?, val texto: String,
    /** false = no se pudo comprobar cantidad × precio = importe: revisar con atención. */
    val cuadra: Boolean
)

data class FacturaLeida(
    val proveedor: ProvInfo?, val numero: String?, val fechaIso: String?,
    val base: Double?, val iva: Double?, val total: Double?,
    val lineas: List<LineaLeida>, val sinReconocer: List<String>
)

/**
 * Interpreta el texto de una factura (filas ya ordenadas, una por renglón del papel) con reglas, sin IA: NIF del proveedor,
 * número, fecha, totales y líneas. Las líneas solo se reconocen cuando llevan la REF de un artículo que ya existe; el resto se
 * lista aparte para añadirlo a mano. Todo se enseña para revisarlo: el OCR confunde cifras y nada se da por bueno.
 */
object ParserFactura {
    private val IMPORTE = Regex("""(?<![\d.,])-?\d{1,3}(?:\.\d{3})+,\d{2}(?!\d)|(?<![\d.,])-?\d+,\d{2}(?!\d)|(?<![\d.,])-?\d+\.\d{2}(?!\d)""")
    private val FECHA = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})\b""")
    private val NIF_CIF = Regex("""\b(?:ES)?([ABCDEFGHJNPQRSUVW])[-\s.]?(\d{7})[-\s.]?([0-9A-J])\b""", RegexOption.IGNORE_CASE)
    private val NIF_DNI = Regex("""\b(?:ES)?(\d{8})[-\s.]?([A-Z])\b""", RegexOption.IGNORE_CASE)
    private val NIF_NIE = Regex("""\b(?:ES)?([XYZ])[-\s.]?(\d{7})[-\s.]?([A-Z])\b""", RegexOption.IGNORE_CASE)

    fun sinAcentos(t: String): String = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
    private fun norm(t: String) = t.uppercase().filter { it.isLetterOrDigit() }

    /** "1.234,56" → 1234.56; "12,50" → 12.5; "12.50" → 12.5; "7" → 7.0. */
    fun numero(token: String): Double? {
        var t = token.trim().trimEnd('€', '%')
        if (t.isEmpty() || !t.any { it.isDigit() }) return null
        if (!Regex("""-?[\d.,]+""").matches(t)) return null
        t = if (t.contains(',')) t.replace(".", "").replace(',', '.') else if (Regex("""-?\d{1,3}(\.\d{3})+""").matches(t)) t.replace(".", "") else t
        return t.toDoubleOrNull()
    }

    private fun importesDe(fila: String): List<Double> = IMPORTE.findAll(fila).mapNotNull { numero(it.value) }.toList()

    /** El OCR lee a menudo un 0 como "O" y un 1 como "I" o "l": en un sitio donde solo caben cifras se corrigen. */
    private fun cifras(t: String) = t.map { when (it) { 'O', 'o' -> '0'; 'I', 'l', '|' -> '1'; else -> it } }.joinToString("")

    private val NIF_OCR = Regex("""\b(?:ES)?([ABCDEFGHJNPQRSUVW])[-\s.]?([0-9OoIl|]{7})[-\s.]?([0-9A-JOoIl])\b""", RegexOption.IGNORE_CASE)
    private val FECHA_OCR = Regex("""\b([0-9OoIl]{1,2})[/\-.]([0-9OoIl]{1,2})[/\-.]([0-9OoIl]{4}|[0-9OoIl]{2})\b""")

    fun nifsDe(texto: String): List<String> {
        val out = mutableListOf<String>()
        NIF_CIF.findAll(texto).forEach { out += (it.groupValues[1] + it.groupValues[2] + it.groupValues[3]).uppercase() }
        NIF_OCR.findAll(texto).forEach { out += (it.groupValues[1] + cifras(it.groupValues[2]) + cifras(it.groupValues[3]).let { c -> if (it.groupValues[3].first().isLetter() && it.groupValues[3].first() !in "OoIl") it.groupValues[3] else c }).uppercase() }
        NIF_NIE.findAll(texto).forEach { out += (it.groupValues[1] + it.groupValues[2] + it.groupValues[3]).uppercase() }
        NIF_DNI.findAll(texto).forEach { out += (it.groupValues[1] + it.groupValues[2]).uppercase() }
        return out
    }

    fun fechaIso(dia: String, mes: String, anio: String): String? {
        val d = dia.toInt(); val m = mes.toInt(); var a = anio.toInt()
        if (anio.length == 2) a += 2000
        if (m !in 1..12 || d !in 1..31 || a !in 2000..2100) return null
        return "%04d-%02d-%02d".format(a, m, d)
    }

    /** Distancia de edición, para tolerar un carácter mal leído en una REF larga. */
    private fun distancia(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }

    private fun codigosDe(p: ProdInfo): List<String> = listOfNotNull(p.ref, p.sku?.substringAfter('/', ""))
        .map { norm(it) }.filter { it.length >= 3 }.distinct()

    private fun coincide(token: String, codigo: String) =
        token == codigo || (codigo.length >= 6 && token.length >= 5 && distancia(token, codigo) <= 1)

    fun interpretar(filas: List<String>, proveedores: List<ProvInfo>, nifPropio: String?, productos: List<ProdInfo>): FacturaLeida {
        val propio = nifPropio?.let { norm(it) }
        val texto = filas.joinToString("\n")

        // ---- Proveedor: por NIF (el de la tienda no cuenta) o, si no, por su nombre.
        val nifs = nifsDe(texto).map { norm(it) }.filter { it != propio }
        var proveedor = proveedores.firstOrNull { p -> p.nif?.let { norm(it) } in nifs && !p.nif.isNullOrBlank() }
        if (proveedor == null) {
            val t = sinAcentos(texto)
            proveedor = proveedores.filter { it.nombre.trim().length >= 4 && t.contains(sinAcentos(it.nombre.trim())) }.maxByOrNull { it.nombre.length }
        }

        // ---- Fecha: la de la fila "fecha" (no la de vencimiento) o la primera del documento.
        // Si la fila trae también la de vencimiento ("Fecha: 03/10/2026  Vencimiento: 02/11/2026"), vale solo lo que va antes.
        fun fechaDe(f: String): String? {
            val i = sinAcentos(f).indexOf("venc")
            val tramo = if (i >= 0) f.substring(0, minOf(i, f.length)) else f
            return FECHA_OCR.find(tramo)?.let { fechaIso(cifras(it.groupValues[1]), cifras(it.groupValues[2]), cifras(it.groupValues[3])) }
        }
        val fecha = filas.firstOrNull { sinAcentos(it).contains("fecha") && fechaDe(it) != null }?.let { fechaDe(it) }
            ?: filas.firstOrNull { fechaDe(it) != null }?.let { fechaDe(it) }

        // ---- Número de factura: el primer código con algún dígito tras la palabra "factura"/"fra"/"invoice" (o en la fila siguiente).
        fun candidatoNumero(f: String): String? = f.split(Regex("""[\s:]+""")).map { it.trim('#', '.', ',', ':', ';') }.firstOrNull { t ->
            t.length in 3..22 && t.any { it.isDigit() } && Regex("""[A-Za-z0-9][A-Za-z0-9\-/._]*""").matches(t) &&
                !FECHA.matches(t) && nifsDe(t).isEmpty() && !t.matches(Regex("""[\d.]+,\d{2}""")) && t.uppercase() != propio
        }
        var numero: String? = null
        for ((i, f) in filas.withIndex()) {
            val m = Regex("""(?i)\b(factura|fra\.?|invoice|n[úu]m(?:ero)?\.?\s*(?:de\s*)?fact\w*)""").find(f) ?: continue
            numero = candidatoNumero(f.substring(m.range.last + 1)) ?: filas.getOrNull(i + 1)?.let { candidatoNumero(it) }
            if (numero != null) break
        }

        // ---- Totales (solo informativos: la factura se calcula con las líneas).
        fun ultimoImporte(regla: (String) -> Boolean): Double? =
            filas.filter(regla).flatMap { importesDe(it).takeLast(1) }.lastOrNull()
        val total = filas.filter { val t = sinAcentos(it); t.contains("total") && !t.contains("subtotal") && !t.contains("base") }
            .flatMap { importesDe(it) }.maxOrNull() ?: filas.flatMap { importesDe(it) }.maxOrNull()
        val base = ultimoImporte { val t = sinAcentos(it); t.contains("base") || t.contains("subtotal") || t.contains("importe neto") }
        val iva = ultimoImporte { val t = sinAcentos(it); Regex("""\biva\b""").containsMatchIn(t) || t.contains("cuota") }

        // ---- Líneas: filas con importes y una REF conocida (del proveedor, si se ha identificado).
        val candidatos = productos.filter { proveedor == null || it.proveedorId == null || it.proveedorId == proveedor.id }
        val lineas = mutableListOf<LineaLeida>()
        val sin = mutableListOf<String>()
        val yaVistos = mutableSetOf<Long>()
        for (fila in filas) {
            val tokens = fila.split(Regex("""\s+""")).filter { it.isNotBlank() }
            val enFila = importesDe(fila)
            if (enFila.isEmpty()) continue
            val prod = candidatos.firstNotNullOfOrNull { p ->
                val cods = codigosDe(p)
                if (tokens.any { tk -> val n = norm(tk); n.isNotEmpty() && cods.any { c -> coincide(n, c) } }) p else null
            }
            if (prod == null) {
                val t = sinAcentos(fila)
                if (!listOf("total", "base", "iva", "subtotal", "cuota", "vencimiento", "recargo", "pagina", "tel", "cif", "nif", "fecha").any { t.contains(it) } && tokens.size >= 3) sin += fila
                continue
            }
            val codigos = codigosDe(prod)
            val numericos = tokens.filter { tk -> codigos.none { c -> norm(tk) == c } }.mapNotNull { tk -> numero(tk)?.let { v -> tk to v } }
                .filter { !it.first.endsWith("%") }
            if (numericos.size < 2) { sin += fila; continue }
            val importe = numericos.last().second
            val previos = numericos.dropLast(1)
            // Cantidad (entera) y precio que multiplicados dan el importe; la cantidad va antes que el precio.
            var cant: Int? = null; var coste: Double? = null
            outer@ for (i in previos.indices) {
                val q = previos[i].second
                if (q != Math.floor(q) || q < 1 || q > 9999) continue
                for (j in i + 1 until previos.size) {
                    val p = previos[j].second
                    if (p > 0 && Math.abs(q * p - importe) <= maxOf(0.03, 0.01 * Math.abs(importe))) { cant = q.toInt(); coste = p; break@outer }
                }
            }
            var cuadra = true
            if (cant == null) {
                // Sin coincidencia exacta (p. ej. hay descuento): primera cantidad entera plausible y precio = importe / cantidad.
                val q = previos.firstOrNull { it.second == Math.floor(it.second) && it.second in 1.0..9999.0 }?.second
                if (q == null) { sin += fila; continue }
                cant = q.toInt(); coste = Math.round(importe / q * 10000) / 10000.0; cuadra = false
            }
            if (!yaVistos.add(prod.id * 1000 + cant)) { /* misma REF y cantidad otra vez: se deja, puede ser otra línea */ }
            lineas += LineaLeida(prod, cant, coste!!, importe, fila, cuadra)
        }
        return FacturaLeida(proveedor, numero, fecha, base, iva, total, lineas, sin)
    }
}
