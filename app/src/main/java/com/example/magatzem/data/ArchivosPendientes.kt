package com.example.magatzem.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ficheros recibidos de MiTPV (cierres de caja) que todavía no se han procesado: los .json de la carpeta `recibidos` de la app. Al abrir
 * la app se avisa si hay alguno y se ofrece importarlo; una vez importado, el fichero se borra. Mientras haya alguno pendiente no se puede
 * exportar de Magatzem a MiTPV.
 */
object ArchivosPendientes {
    fun carpeta(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "recibidos").also { it.mkdirs() }

    /** De más antiguo a más reciente por nombre (los cierres se importan en orden). */
    fun listar(context: Context): List<File> =
        carpeta(context).listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }?.sortedBy { it.name }.orEmpty()

    fun hay(context: Context): Boolean = listar(context).isNotEmpty()

    /** Copia lo recibido (p. ej. por Compartir) a la carpeta de pendientes y devuelve el fichero; null si no se pudo leer. */
    fun guardar(context: Context, uri: Uri, nombre: String?): File? = runCatching {
        val limpio = (nombre ?: "cierre.json").filter { it.isLetterOrDigit() || it in "._-" }.ifBlank { "cierre.json" }
        val destino = File(carpeta(context), SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault()).format(Date()) + "_" +
            if (limpio.endsWith(".json", true)) limpio else "$limpio.json")
        context.contentResolver.openInputStream(uri)!!.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
        destino
    }.getOrNull()
}
