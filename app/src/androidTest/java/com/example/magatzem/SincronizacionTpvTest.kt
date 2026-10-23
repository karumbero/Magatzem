package com.example.magatzem

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.magatzem.data.AppDatabase
import com.example.magatzem.data.tpv.SincronizacionTpv
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sincronizar con un TPV de verdad (el MiTPV del propio emulador, por 127.0.0.1, que entra sin autorizar). Hace falta el PIN de un usuario de nivel 1 del TPV, que no se guarda en el
 * código: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.pinTpv=NNNNNN`. Sin el PIN, la prueba se salta. CAMBIA los datos de Almacén de ese TPV.
 */
@RunWith(AndroidJUnit4::class)
class SincronizacionTpvTest {
    @Test
    fun laBaseDeMagatzemLlegaAlTpv() = runBlocking {
        val pin = InstrumentationRegistry.getArguments().getString("pinTpv")
        assumeTrue("sin PIN del TPV (pinTpv)", pin != null)
        val c = ApplicationProvider.getApplicationContext<Context>()
        SincronizacionTpv.guardarDireccion(c, "http://127.0.0.1:${SincronizacionTpv.PUERTO}")
        assertTrue("MiTPV no responde en el aparato", SincronizacionTpv.alcanzable(c))
        SincronizacionTpv.entrar(c, pin!!)
        val r = SincronizacionTpv.sincronizar(c)
        assertTrue(r.optBoolean("ok"))
        val tablas = r.getJSONObject("tablas")
        val db = AppDatabase.getInstance(c).openHelper.readableDatabase
        for (t in listOf("productos", "proveedores", "recepciones", "facturas", "albaranes")) {
            val locales = db.query("SELECT COUNT(*) FROM $t").use { it.moveToFirst(); it.getLong(0) }
            assertEquals("filas de $t en el TPV", locales, tablas.getLong(t))
        }
    }
}
