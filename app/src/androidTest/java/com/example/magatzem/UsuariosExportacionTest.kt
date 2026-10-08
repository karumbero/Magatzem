package com.example.magatzem

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.magatzem.data.AppDatabase
import com.example.magatzem.data.CatalogoExportador
import com.example.magatzem.data.UsuarioEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A MiTPV solo viajan los usuarios de nivel 1 de Magatzem, en todas las exportaciones; un cambio en ellos cuenta como algo que exportar. */
@RunWith(AndroidJUnit4::class)
class UsuariosExportacionTest {
    private lateinit var db: AppDatabase
    private val archivos = mutableListOf<java.io.File?>()

    @Before
    fun preparar() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).allowMainThreadQueries().build()
        db.usuarioDao().insert(UsuarioEntity(nombre = "Jefa", pin = "111111", nivel = 1, uuid = "u-jefa"))
        db.usuarioDao().insert(UsuarioEntity(nombre = "Empleada", pin = "222222", nivel = 2, uuid = "u-emp"))
        Unit
    }

    @After
    fun cerrar() {
        archivos.forEach { it?.delete() }
        db.close()
    }

    private fun nombres(f: java.io.File): List<String> {
        val a = JSONObject(f.readText()).getJSONArray("usuarios")
        return List(a.length()) { a.getJSONObject(it).getString("nombre") }
    }

    @Test
    fun soloViajanLosUsuariosDeNivel1EnTodasLasExportaciones() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val exportador = CatalogoExportador(ctx, db, "test")
        val primera = exportador.exportar().also { archivos += it }
        assertNotNull(primera)
        assertEquals(listOf("Jefa"), nombres(primera!!))
        // Sin cambios no hay nada que exportar.
        assertNull(exportador.exportar().also { archivos += it })
        // Un usuario de nivel 2 nuevo o editado no cuenta...
        db.usuarioDao().insert(UsuarioEntity(nombre = "Otra", pin = "333333", nivel = 3, uuid = "u-otra"))
        assertNull(exportador.exportar().also { archivos += it })
        // ...pero sí un cambio en uno de nivel 1 (p. ej. su PIN), y también un nivel 1 nuevo.
        db.usuarioDao().update(db.usuarioDao().obtenerTodos().first { it.uuid == "u-jefa" }.copy(pin = "999999"))
        val segunda = exportador.exportar().also { archivos += it }
        assertNotNull(segunda)
        assertEquals(listOf("Jefa"), nombres(segunda!!))
        db.usuarioDao().insert(UsuarioEntity(nombre = "Socia", pin = "444444", nivel = 1, uuid = "u-socia"))
        val tercera = exportador.exportar().also { archivos += it }
        assertEquals(setOf("Jefa", "Socia"), nombres(tercera!!).toSet())
    }
}
