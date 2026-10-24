package com.example.magatzem.data.escaneo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

class LineaEscaneada(val ref: String, val descripcion: String, val cantidad: Double, val precio: Double?, val dto: Double?)
class PlazoEscaneado(val fechaIso: String, val importe: Double?)

/** Lo que manda la app Escaneo (JSON): una factura o un albarán leído de un PDF o una foto, ya revisado allí. */
class FacturaEscaneada(
    val esAlbaran: Boolean, val proveedor: String, val numero: String, val fechaIso: String,
    val base: Double?, val iva: Double?, val total: Double?, val formaPago: String,
    val plazos: List<PlazoEscaneado>, val lineas: List<LineaEscaneada>,
    /** Descuento comercial del documento en % sobre las líneas a precio de lista (no cambia coste ni PVP: es beneficio). 0 si no hay. */
    val descuentoPct: Double = 0.0
)

/** Acción con la que Escaneo manda una factura a Magatzem (la leída viaja en el extra [EXTRA_JSON]). */
const val ACCION_IMPORTAR_ESCANEO = "com.example.magatzem.action.IMPORTAR_ESCANEO"
const val EXTRA_JSON = "json"

/** La factura recibida de Escaneo que espera a que alguien la revise y le dé entrada (en cuanto haya sesión). */
object ImportacionEscaneo {
    var pendiente by mutableStateOf<FacturaEscaneada?>(null)
    var error by mutableStateOf<String?>(null)

    fun recibir(json: String?) {
        error = null
        if (json.isNullOrBlank()) { error = "Escaneo no ha mandado ningún dato."; return }
        try { pendiente = leer(JSONObject(json)) } catch (e: Exception) { error = "No se ha podido leer la factura de Escaneo: ${e.message ?: e.javaClass.simpleName}" }
    }

    fun leer(o: JSONObject): FacturaEscaneada {
        fun d(k: String): Double? = o.optDouble(k).takeIf { !it.isNaN() }
        val fecha = o.optString("fecha")
        require(Regex("""\d{4}-\d{2}-\d{2}""").matches(fecha)) { "falta la fecha (aaaa-mm-dd)" }
        val lineas = o.optJSONArray("lineas")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty().map { l ->
            LineaEscaneada(l.optString("ref").trim(), l.optString("descripcion").trim(), l.optDouble("cantidad", 0.0), l.optDouble("precio").takeIf { !it.isNaN() }, l.optDouble("dto").takeIf { !it.isNaN() })
        }
        require(lineas.isNotEmpty()) { "no trae líneas" }
        val plazos = o.optJSONArray("plazos")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
            .filter { Regex("""\d{4}-\d{2}-\d{2}""").matches(it.optString("fecha")) }.map { PlazoEscaneado(it.getString("fecha"), it.optDouble("importe").takeIf { v -> !v.isNaN() }) }
        return FacturaEscaneada(
            o.optString("tipo") == "albaran", o.optString("proveedor").trim(), o.optString("numero").trim(), fecha,
            d("base"), d("iva"), d("total"), o.optString("formaPago").trim(), plazos, lineas, (d("descuentoPct") ?: 0.0).coerceIn(0.0, 99.99)
        )
    }
}
