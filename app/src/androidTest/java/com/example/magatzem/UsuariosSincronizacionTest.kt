package com.example.magatzem

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.magatzem.data.AppDatabase
import com.example.magatzem.data.CierreImportador
import com.example.magatzem.data.TRIGGERS_USUARIOS
import com.example.magatzem.data.UsuarioEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Los usuarios de nivel 1 se sincronizan con MiTPV (aquí, el sentido MiTPV → Magatzem por el cierre): gana el cambio más reciente. */
@RunWith(AndroidJUnit4::class)
class UsuariosSincronizacionTest {
    private lateinit var db: AppDatabase

    @Before
    fun preparar() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries().addCallback(TRIGGERS_USUARIOS).build()
        // Fuerza la apertura para que se creen los disparadores.
        db.openHelper.writableDatabase
    }

    @After
    fun cerrar() {
        db.close()
    }

    private fun usuario(nombre: String, pin: String, nivel: Int, uuid: String, mod: Long = 0, activo: Boolean = true) =
        JSONObject().put("uuid", uuid).put("nombre", nombre).put("pin", pin).put("nivel", nivel).put("activo", activo).put("modificadoEn", mod)

    private fun cierre(vararg usuarios: JSONObject) = JSONObject()
        .put("formato", "mitpv-cierre").put("version", 1).put("uuid", "c-" + usuarios.size + System.nanoTime()).put("origen", "test")
        .put("abiertoEn", JSONObject.NULL).put("cerradoEn", "2026-10-07 20:00:00")
        .put("cajero", JSONObject().put("uuid", "x").put("nombre", "X"))
        .put(
            "caja", JSONObject().put("saldoInicial", 0.0).put("ventaContadoTickets", 0.0).put("ventaTarjetaTickets", 0.0)
                .put("totalDatafono", 0.0).put("ajusteTarjeta", 0.0).put("ventaContado", 0.0).put("ventaTarjeta", 0.0)
                .put("efectivoEsperado", 0.0).put("efectivoContado", 0.0).put("diferencia", 0.0)
                .put("numTickets", 0).put("primerTicket", JSONObject.NULL).put("ultimoTicket", JSONObject.NULL)
        )
        .put("retiradas", JSONArray()).put("tickets", JSONArray())
        .put("usuariosNivel1", JSONArray(usuarios.toList())).toString()

    @Test
    fun losDisparadoresAnotanLaFechaDeLosCambios() = runBlocking {
        val dao = db.usuarioDao()
        val id = dao.insert(UsuarioEntity(nombre = "A", pin = "111111", nivel = 1, uuid = "ua"))
        val creado = dao.obtenerPorId(id)!!.modificadoEn
        assertTrue("al crear se anota la fecha", creado > 0)
        // Un cambio de PIN sin fecha propia la actualiza (se parte de una fecha antigua para poder verlo).
        dao.update(dao.obtenerPorId(id)!!.copy(modificadoEn = 1000))
        dao.update(dao.obtenerPorId(id)!!.copy(pin = "222222"))
        assertTrue(dao.obtenerPorId(id)!!.modificadoEn > 1000)
        // Un cambio con fecha propia (el de la sincronización) la respeta.
        dao.update(dao.obtenerPorId(id)!!.copy(pin = "333333", modificadoEn = 5000))
        assertEquals(5000L, dao.obtenerPorId(id)!!.modificadoEn)
    }

    @Test
    fun elCierreCreaActualizaOIgnoraSegunLaFecha() = runBlocking {
        val dao = db.usuarioDao()
        dao.insert(UsuarioEntity(nombre = "Jefa", pin = "111111", nivel = 1, uuid = "u-jefa", modificadoEn = 2000))
        dao.insert(UsuarioEntity(nombre = "Socia", pin = "222222", nivel = 1, uuid = "u-socia", modificadoEn = 9000))
        val importador = CierreImportador(db)
        importador.importar(
            cierre(
                usuario("Jefa Nueva", "999999", 1, "u-jefa", mod = 3000),            // más reciente: se aplica
                usuario("Socia Vieja", "000000", 1, "u-socia", mod = 4000),          // más antiguo que el de aquí: se ignora
                usuario("Recién creada", "444444", 1, "u-nueva", mod = 6000),        // nueva: se crea
                usuario("Cajera", "555555", 2, "u-cajera", mod = 7000)               // otro nivel: no se sincroniza
            ),
            "c.json"
        )
        val jefa = dao.obtenerPorUuid("u-jefa")!!
        assertEquals("Jefa Nueva", jefa.nombre); assertEquals("999999", jefa.pin); assertEquals(3000L, jefa.modificadoEn)
        assertEquals("Socia", dao.obtenerPorUuid("u-socia")!!.nombre)
        val nueva = dao.obtenerPorUuid("u-nueva")!!
        assertEquals("Recién creada", nueva.nombre); assertEquals(6000L, nueva.modificadoEn); assertTrue(nueva.activo)
        assertNull(dao.obtenerPorUuid("u-cajera"))
    }

    @Test
    fun desactivarUnNivel1EnMiTPVLlegaAMagatzem() = runBlocking {
        val dao = db.usuarioDao()
        dao.insert(UsuarioEntity(nombre = "Jefa", pin = "111111", nivel = 1, uuid = "u-jefa", modificadoEn = 1000))
        CierreImportador(db).importar(cierre(usuario("Jefa", "111111", 1, "u-jefa", mod = 2000, activo = false)), "d.json")
        assertTrue(!dao.obtenerPorUuid("u-jefa")!!.activo)
    }
}
