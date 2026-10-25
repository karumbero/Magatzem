package com.example.magatzem

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.magatzem.data.CapaInicial
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Capa inicial de coste: las unidades sin capa de un artículo con coste pasan a tener una; no se toca ningún dato del artículo y se puede repetir. */
@RunWith(AndroidJUnit4::class)
class CapaInicialTest {
    private val ahora = "2026-10-25 10:00:00"
    private fun producto(prov: Long, sku: String, coste: Double, existencia: Int) =
        ProductoEntity(sku = sku, nombre = sku, proveedorId = prov, coste = coste, margenBeneficio = 50.0, precioVenta = 7.0, existencia = existencia, fechaCreacion = ahora, fechaActualizacion = ahora)

    @Test
    fun regularizaSoloLoQueFaltaSinTocarLosArticulos() = runBlocking {
        val db = (ApplicationProvider.getApplicationContext<Application>() as MagatzemApplication).database
        val prov = db.proveedorDao().insert(ProveedorEntity(nombre = "Prov capa ${System.nanoTime()}"))
        val sufijo = System.nanoTime()
        val conCoste = db.productoDao().insert(producto(prov, "$prov/A$sufijo", 2.0, 10))
        val sinCoste = db.productoDao().insert(producto(prov, "$prov/B$sufijo", 0.0, 5))
        val sinExistencia = db.productoDao().insert(producto(prov, "$prov/C$sufijo", 3.0, 0))
        val antes = listOf(conCoste, sinCoste, sinExistencia).map { db.productoDao().obtenerPorId(it)!! }

        CapaInicial.regularizar(db)

        // Solo el artículo con coste y existencia recibe su capa, por todas sus unidades; los demás, nada.
        assertEquals(10, db.consumoCapaDao().capasDisponibles(conCoste).sumOf { it.restante })
        assertEquals(0, db.consumoCapaDao().capasDisponibles(sinCoste).size)
        assertEquals(0, db.consumoCapaDao().capasDisponibles(sinExistencia).size)
        // Ningún dato de los artículos cambia (ni su fecha de actualización).
        assertEquals(antes, listOf(conCoste, sinCoste, sinExistencia).map { db.productoDao().obtenerPorId(it)!! })
        // Repetirlo no crea nada más.
        CapaInicial.regularizar(db)
        assertEquals(1, db.consumoCapaDao().capasDisponibles(conCoste).size)

        // Al ponerle coste al que no tenía, y completar, recibe su capa.
        db.productoDao().update(db.productoDao().obtenerPorId(sinCoste)!!.copy(coste = 4.0))
        assertEquals(5, CapaInicial.completar(db, sinCoste))
        assertEquals(5, db.consumoCapaDao().capasDisponibles(sinCoste).sumOf { it.restante })
        // Solo las unidades sin capa: si suben las unidades, se crea capa por las nuevas.
        db.productoDao().update(db.productoDao().obtenerPorId(conCoste)!!.copy(existencia = 14))
        assertEquals(4, CapaInicial.completar(db, conCoste))
        assertEquals(14, db.consumoCapaDao().capasDisponibles(conCoste).sumOf { it.restante })
        assertTrue(db.consumoCapaDao().capasDisponibles(conCoste).size == 2)
    }
}
