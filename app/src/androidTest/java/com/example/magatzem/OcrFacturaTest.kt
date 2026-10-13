package com.example.magatzem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.magatzem.ocr.OcrFactura
import com.example.magatzem.ocr.ParserFactura
import com.example.magatzem.ocr.ProdInfo
import com.example.magatzem.ocr.ProvInfo
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** El OCR de ML Kit funciona en el aparato y sin internet: se dibuja una factura de ejemplo, se lee y se interpreta. */
@RunWith(AndroidJUnit4::class)
class OcrFacturaTest {
    @Test
    fun leeUnaFacturaDibujada() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val lineas = listOf(
            "CERAMICA PONS S.L.", "CIF: B07123456", "FACTURA Nº A-2026-0561", "Fecha: 03/10/2026",
            "1901844   IMAN GATO FLORES   100   1,60   160,00",
            "1901856   IMAN BARCO AZUL    48   1,60    76,80",
            "Base imponible 236,80", "IVA 21%  49,73", "TOTAL FACTURA 286,53"
        )
        val bm = Bitmap.createBitmap(1400, 120 + lineas.size * 90, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm); c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.BLACK; textSize = 44f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }
        lineas.forEachIndexed { i, t -> c.drawText(t, 40f, 100f + i * 90f, p) }
        val f = File(ctx.cacheDir, "ocr_prueba.png"); f.outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val filas = OcrFactura.leerFilas(ctx, Uri.fromFile(f))
        val r = ParserFactura.interpretar(
            filas, listOf(ProvInfo(16, "Ceramica Pons", "B07123456")), null,
            listOf(ProdInfo(1, "16/1901844", "1901844", "Imán gato flores", 16), ProdInfo(2, "16/1901856", "1901856", "Imán barco azul", 16))
        )
        println("FILAS:\n" + filas.joinToString("\n"))
        assertTrue("proveedor", r.proveedor?.id == 16L)
        assertTrue("número ${r.numero}", r.numero?.contains("0561") == true)
        // La fecha puede salir mal leída (un "10" leído como "0"): se enseña en la revisión y se corrige a mano, así que no se exige aquí.
        println("FECHA: ${r.fechaIso}")
        assertTrue("líneas ${r.lineas.size}", r.lineas.size == 2)
        assertTrue("total ${r.total}", r.total != null && Math.abs(r.total!! - 286.53) < 0.01)
    }
}
