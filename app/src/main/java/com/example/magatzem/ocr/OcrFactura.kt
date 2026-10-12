package com.example.magatzem.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OCR en el propio aparato (ML Kit, modelo incluido en la app: no necesita internet). Lee una foto o un PDF (cada página se
 * dibuja como imagen) y devuelve el texto en filas, una por renglón del papel, de izquierda a derecha.
 */
object OcrFactura {
    private const val MAX_PAGINAS = 6

    suspend fun leerFilas(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val tipo = context.contentResolver.getType(uri).orEmpty()
            val filas = mutableListOf<String>()
            if (tipo.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")) {
                paginasPdf(context, uri).forEach { bm ->
                    filas += agrupar(Tasks.await(reconocedor.process(InputImage.fromBitmap(bm, 0))))
                    bm.recycle()
                }
            } else {
                // fromFilePath respeta la rotación guardada por la cámara.
                filas += agrupar(Tasks.await(reconocedor.process(InputImage.fromFilePath(context, uri))))
            }
            filas
        } finally {
            reconocedor.close()
        }
    }

    private fun paginasPdf(context: Context, uri: Uri): List<Bitmap> {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("No se puede abrir el PDF")
        val out = mutableListOf<Bitmap>()
        fd.use {
            PdfRenderer(it).use { r ->
                for (i in 0 until minOf(r.pageCount, MAX_PAGINAS)) {
                    r.openPage(i).use { p ->
                        val ancho = 2000
                        val alto = (ancho.toFloat() * p.height / p.width).toInt()
                        val bm = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
                        Canvas(bm).drawColor(Color.WHITE)
                        p.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out += bm
                    }
                }
            }
        }
        return out
    }

    /** Une los trozos de texto que están a la misma altura en una sola fila (la tabla de líneas de la factura). */
    private fun agrupar(texto: Text): List<String> {
        class Trozo(val cy: Float, val alto: Float, val x: Int, val texto: String)
        val trozos = texto.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
            l.boundingBox?.let { Trozo((it.top + it.bottom) / 2f, (it.bottom - it.top).toFloat(), it.left, l.text) }
        }.sortedBy { it.cy }
        if (trozos.isEmpty()) return emptyList()
        val altos = trozos.map { it.alto }.sorted()
        val umbral = altos[altos.size / 2] * 0.6f
        val filas = mutableListOf<MutableList<Trozo>>()
        for (t in trozos) {
            val fila = filas.lastOrNull()
            if (fila != null && Math.abs(t.cy - fila.map { it.cy }.average().toFloat()) < umbral) fila += t else filas += mutableListOf(t)
        }
        return filas.map { f -> f.sortedBy { it.x }.joinToString("  ") { it.texto } }
    }
}
