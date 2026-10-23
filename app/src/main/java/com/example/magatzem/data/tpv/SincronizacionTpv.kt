package com.example.magatzem.data.tpv

import android.content.Context
import com.example.magatzem.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TpvError(mensaje: String, val codigo: Int) : Exception(mensaje)
class TpvNoAutorizado : Exception("Este aparato no está autorizado en el TPV: pulsa «Autorizar este aparato» y confírmalo en el TPV (Ajustes → Aparatos).")

/**
 * Sincronización de Magatzem → TPV, en un sola dirección: manda una copia de la base de datos de Magatzem y el TPV sustituye con ella sus tablas de datos generales y de Almacén
 * (nada de ventas, cierres, horarios ni gastos). El aparato tiene que estar autorizado en el TPV y quien sincroniza, tener un PIN de nivel 1. Ver ApiSincronizacion en MiTPV.
 */
object SincronizacionTpv {
    const val PUERTO = 47831
    private fun prefs(c: Context) = c.getSharedPreferences("tpv", Context.MODE_PRIVATE)

    fun direccion(c: Context): String = prefs(c).getString("direccion", null) ?: "http://192.168.1.250:$PUERTO"
    fun guardarDireccion(c: Context, texto: String) {
        var t = texto.trim().trimEnd('/')
        if (t.isEmpty()) t = "192.168.1.250"
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "http://$t"
        if (!t.substringAfter("://").contains(':')) t = "$t:$PUERTO"
        prefs(c).edit().putString("direccion", t).apply()
    }
    private fun dispositivo(c: Context): String? = prefs(c).getString("dispositivo", null)
    @Volatile private var sid: String? = null
    val haySesion: Boolean get() = sid != null

    private suspend fun llamar(c: Context, metodo: String, ruta: String, cuerpo: ByteArray? = null, tipo: String = "application/json; charset=utf-8", archivo: File? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val con = URL(direccion(c) + ruta).openConnection() as HttpURLConnection
            try {
                con.requestMethod = metodo; con.connectTimeout = 8000; con.readTimeout = 120000; con.instanceFollowRedirects = false
                val galletas = listOfNotNull(dispositivo(c)?.let { "tpv_dev=$it" }, sid?.let { "tpv_sid=$it" })
                if (galletas.isNotEmpty()) con.setRequestProperty("Cookie", galletas.joinToString("; "))
                if (cuerpo != null || archivo != null) {
                    con.doOutput = true; con.setRequestProperty("Content-Type", tipo)
                    if (archivo != null) { con.setFixedLengthStreamingMode(archivo.length()); con.outputStream.use { o -> archivo.inputStream().use { it.copyTo(o) } } }
                    else con.outputStream.use { it.write(cuerpo!!) }
                }
                val codigo = con.responseCode
                con.headerFields["Set-Cookie"]?.forEach { h ->
                    val par = h.substringBefore(';'); val nombre = par.substringBefore('='); val valor = par.substringAfter('=', "")
                    if (nombre == "tpv_dev" && valor.isNotEmpty()) prefs(c).edit().putString("dispositivo", valor).apply()
                    if (nombre == "tpv_sid" && valor.isNotEmpty()) sid = valor
                }
                val texto = (if (codigo < 400) con.inputStream else con.errorStream)?.bufferedReader()?.readText().orEmpty()
                val json = runCatching { JSONObject(texto) }.getOrDefault(JSONObject())
                if (codigo in 300..399) throw TpvNoAutorizado()
                if (codigo >= 400) {
                    val mensaje = json.optString("error", "Error $codigo")
                    if (codigo == 401 && mensaje.contains("no está autorizado")) throw TpvNoAutorizado()
                    if (codigo == 401 && !ruta.contains("auth.php")) sid = null
                    throw TpvError(mensaje, codigo)
                }
                json
            } finally { con.disconnect() }
        }

    /** ¿Responde el TPV en esa dirección? */
    suspend fun alcanzable(c: Context): Boolean = runCatching { llamar(c, "GET", "/tienda/api/servidor.php").optBoolean("tpv") }.getOrDefault(false)

    /** Entra con el PIN de un usuario de nivel 1 del TPV (se necesita para sincronizar; no se guarda). */
    suspend fun entrar(c: Context, pin: String) {
        llamar(c, "POST", "/tienda/api/auth.php", JSONObject().put("accion", "entrar").put("area", "oficina").put("pin", pin).toString().toByteArray())
    }

    suspend fun pedirAutorizacion(c: Context, nombre: String): Pair<String, String> {
        val j = llamar(c, "POST", "/tienda/api/dispositivo.php", JSONObject().put("nombre", nombre).toString().toByteArray())
        return j.getString("id") to j.getString("codigo")
    }
    suspend fun estadoAutorizacion(c: Context, id: String): String = llamar(c, "GET", "/tienda/api/dispositivo.php?id=$id").optString("estado")

    /** Una copia coherente de la base de datos de Magatzem (con lo que aún no se ha volcado del registro). */
    private suspend fun copiaDeLaBase(c: Context): File = withContext(Dispatchers.IO) {
        val destino = File(c.cacheDir, "sincronizacion.db").also { it.delete() }
        val db = AppDatabase.getInstance(c).openHelper.writableDatabase
        try { db.execSQL("VACUUM INTO '" + destino.absolutePath.replace("'", "''") + "'") }
        catch (e: Exception) {
            destino.delete()
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            File(db.path!!).copyTo(destino, overwrite = true)
        }
        destino
    }

    /** Manda la base al TPV, que sustituye sus tablas por las de Magatzem. Devuelve el resumen (filas por tabla). */
    suspend fun sincronizar(c: Context): JSONObject {
        val copia = copiaDeLaBase(c)
        try { return llamar(c, "POST", "/tienda/api/sincronizacion.php", tipo = "application/octet-stream", archivo = copia) }
        finally { copia.delete() }
    }
}
