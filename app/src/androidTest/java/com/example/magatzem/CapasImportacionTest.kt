package com.example.magatzem

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import com.example.magatzem.data.AppDatabase
import com.example.magatzem.data.CapasIncidencia
import com.example.magatzem.data.CatalogoExportador
import com.example.magatzem.data.CierreImportador
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.data.RecepcionEntity
import com.example.magatzem.data.RecepcionLineaEntity
import com.example.magatzem.data.factorCoste
import com.example.magatzem.ui.documentos.DocumentoMotor
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Importa cierres de MiTPV sobre una base de datos EN MEMORIA (no toca la de la app) y comprueba el coste por capas (FIFO):
 * una venta gasta las capas más antiguas, y una devolución vuelve a la capa de su ticket original con el coste de ese ticket.
 */
@RunWith(AndroidJUnit4::class)
class CapasImportacionTest {
    private lateinit var db: AppDatabase
    private var productoId = 0L
    private var capaA = 0L
    private var capaB = 0L
    private val uuidProducto = "prod-1"
    private val f = factorCoste(false)

    @Before
    fun preparar() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val prov = db.proveedorDao().insert(ProveedorEntity(nombre = "Prov"))
        productoId = db.productoDao().insert(
            ProductoEntity(
                sku = "1/X", nombre = "Artículo", proveedorId = prov, coste = 1.40, precioVenta = 4.84, existencia = 10,
                uuid = uuidProducto, fechaCreacion = "2026-10-01", fechaActualizacion = "2026-10-01"
            )
        )
        // Dos entradas: 4 uds a 1,00 (la más antigua) y 6 uds a 1,40.
        capaA = db.recepcionDao().insertarRecepcion(RecepcionEntity(proveedorId = prov, fecha = "2026-10-01 10:00:00", totalConIva = 0.0))
        capaB = db.recepcionDao().insertarRecepcion(RecepcionEntity(proveedorId = prov, fecha = "2026-10-02 10:00:00", totalConIva = 0.0))
        db.recepcionDao().insertarLineas(
            listOf(
                RecepcionLineaEntity(recepcionId = capaA, productoId = productoId, cantidad = 4, coste = 1.00, margenBeneficio = 0.0, subtotalConIva = 0.0),
                RecepcionLineaEntity(recepcionId = capaB, productoId = productoId, cantidad = 6, coste = 1.40, margenBeneficio = 0.0, subtotalConIva = 0.0)
            )
        )
    }

    @After
    fun cerrar() {
        db.close()
    }

    private fun linea(uuid: String, cantidad: Int) = JSONObject()
        .put("uuid", uuid).put("productoUuid", uuidProducto).put("sku", "1/X").put("codigoBarras", JSONObject.NULL)
        .put("descripcion", "Artículo").put("cantidad", cantidad).put("precioUnitarioConIva", 4.84)
        .put("ivaPorcentaje", 21.0).put("subtotalConIva", 4.84 * cantidad)

    private fun ticket(uuid: String, numero: Int, original: String?, vararg lineas: JSONObject) = JSONObject()
        .put("uuid", uuid).put("numero", numero).put("fecha", "2026-10-06 11:00:00").put("formaPago", "EFECTIVO")
        .put("importeIva", 0.0).put("importeTotal", 0.0)
        .put("ventaOriginalUuid", original ?: JSONObject.NULL).put("cajeroUuid", JSONObject.NULL)
        .put("lineas", JSONArray(lineas.toList()))

    private fun cierre(uuid: String, vararg tickets: JSONObject) = JSONObject()
        .put("formato", "mitpv-cierre").put("version", 1).put("uuid", uuid).put("origen", "test")
        .put("abiertoEn", JSONObject.NULL).put("cerradoEn", "2026-10-06 20:00:00")
        .put("cajero", JSONObject().put("uuid", "c1").put("nombre", "Test"))
        .put(
            "caja", JSONObject().put("saldoInicial", 0.0).put("ventaContadoTickets", 0.0).put("ventaTarjetaTickets", 0.0)
                .put("totalDatafono", 0.0).put("ajusteTarjeta", 0.0).put("ventaContado", 0.0).put("ventaTarjeta", 0.0)
                .put("efectivoEsperado", 0.0).put("efectivoContado", 0.0).put("diferencia", 0.0)
                .put("numTickets", tickets.size).put("primerTicket", JSONObject.NULL).put("ultimoTicket", JSONObject.NULL)
        )
        .put("retiradas", JSONArray()).put("tickets", JSONArray(tickets.toList())).toString()

    private suspend fun costeDe(lineaUuid: String): Double? {
        val c = db.query("SELECT costeUnitario FROM venta_lineas_importadas WHERE uuid = ?", arrayOf(lineaUuid))
        return c.use { if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0) else null }
    }

    private fun consumo(recepcionId: Long): Int =
        db.query("SELECT COALESCE(SUM(cantidad),0) FROM consumos_capa WHERE recepcionId = $recepcionId", null).use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun ventaGastaCapasAntiguasYDevolucionVuelveAlaMisma() = runBlocking {
        val importador = CierreImportador(db)
        // Venta de 5: 4 de la capa A (1,00) y 1 de la B (1,40) → coste medio base 1,08.
        importador.importar(cierre("c1", ticket("t1", 1, null, linea("l1", 5))), "c1.json")
        assertEquals(1.08 * f, costeDe("l1")!!, 1e-6)
        assertEquals(4, consumo(capaA))
        assertEquals(1, consumo(capaB))
        // Venta de 2: sale de la B (queda 5) a 1,40.
        importador.importar(cierre("c2", ticket("t2", 2, null, linea("l2", 2))), "c2.json")
        assertEquals(1.40 * f, costeDe("l2")!!, 1e-6)
        assertEquals(3, consumo(capaB))
        // Devolución de 3 del ticket 1: vuelven a la capa A, con el coste del ticket 1 (no el coste actual).
        val r = importador.importar(cierre("c3", ticket("t3", 3, "t1", linea("l3", -3))), "c3.json")
        assertEquals(1.08 * f, costeDe("l3")!!, 1e-6)
        assertEquals(1, consumo(capaA))
        assertEquals(3, consumo(capaB))
        assertTrue(r.devolucionesSinOrigen.isEmpty())
        // Existencia: 10 − 5 − 2 + 3.
        assertEquals(6, db.productoDao().obtenerPorId(productoId)!!.existencia)
        // Devolución sin ticket original: al coste actual y avisada.
        val r2 = importador.importar(cierre("c4", ticket("t4", 4, null, linea("l4", -1))), "c4.json")
        assertEquals(1.40 * f, costeDe("l4")!!, 1e-6)
        assertEquals(listOf("1/X"), r2.devolucionesSinOrigen)
    }

    @Test
    fun unidadesSinCapaSeValoranAlCosteActual() = runBlocking {
        // 10 uds en capas; la venta de 12 deja 2 sin capa (existencia sin entrada) al coste actual 1,40.
        db.productoDao().sumarExistencia(productoId, 5)
        CierreImportador(db).importar(cierre("c1", ticket("t1", 1, null, linea("l1", 12))), "c1.json")
        val esperado = (4 * 1.00 + 6 * 1.40 + 2 * 1.40) / 12 * f
        assertEquals(esperado, costeDe("l1")!!, 1e-6)
    }

    @Test
    fun incidenciaGastaCapasYRecuperarLasDevuelve() = runBlocking {
        val coste = db.withTransaction {
            CapasIncidencia.gastar(db, productoId, 5, 77, "2026-10-06 12:00:00", 1.40)
        }
        assertEquals(1.08, coste, 1e-9) // 4 uds a 1,00 y 1 a 1,40, en base
        assertEquals(4, consumo(capaA))
        assertEquals(1, consumo(capaB))
        db.withTransaction { CapasIncidencia.liberar(db, 77) }
        assertEquals(0, consumo(capaA))
        assertEquals(0, consumo(capaB))
    }

    @Test
    fun unaLineaDeEntradaNoPuedeQuedarPorDebajoDeLoGastado() = runBlocking {
        CierreImportador(db).importar(cierre("c1", ticket("t1", 1, null, linea("l1", 5))), "c1.json")
        val motor = DocumentoMotor(db)
        // La capa A (entrada con 4 uds) ya tiene 4 gastadas: bajarla a 3 se rechaza y no cambia nada.
        val rechazo = motor.guardarLinea(capaA, productoId, 3, 1.00)
        assertTrue(rechazo.error != null)
        assertEquals(4, db.recepcionDao().obtenerLineas(capaA).first().cantidad)
        // Dejarla en 4 (o subirla) sí se permite, y quitarla del todo no.
        assertTrue(motor.guardarLinea(capaA, productoId, 4, 1.00).error == null)
        assertTrue(motor.quitarLinea(capaA, productoId) != null)
    }

    @Test
    fun primeraExportacionCreaUnaCapaInicialPorArticuloConExistencia() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // Un segundo artículo sin existencia y otro con existencia y sin coste.
        db.productoDao().insert(ProductoEntity(sku = "2/Y", nombre = "Sin stock", coste = 3.0, existencia = 0, uuid = "p2", fechaCreacion = "x", fechaActualizacion = "x"))
        val sinCoste = db.productoDao().insert(ProductoEntity(sku = "3/Z", nombre = "Antiguo", coste = 0.0, existencia = 7, uuid = "p3", fechaCreacion = "x", fechaActualizacion = "x"))
        // Hay histórico (las dos entradas del @Before) y un gasto anotado.
        CierreImportador(db).importar(cierre("c1", ticket("t1", 1, null, linea("l1", 3))), "c1.json")
        assertEquals(2, db.recepcionDao().obtenerRecepcion(capaA)?.let { 2 } ?: 0)
        val archivo = CatalogoExportador(ctx, db, "test").exportar()
        try {
            // El histórico desaparece y queda UNA entrada inicial con una línea por artículo con existencia, a su coste actual.
            val recepciones = db.query("SELECT id FROM recepciones", null).use { c -> generateSequence { if (c.moveToNext()) c.getLong(0) else null }.toList() }
            assertEquals(1, recepciones.size)
            val lineas = db.recepcionDao().obtenerLineas(recepciones[0]).associateBy { it.productoId }
            assertEquals(setOf(productoId, sinCoste), lineas.keys)
            assertEquals(7, lineas[productoId]!!.cantidad) // 10 − 3 vendidas
            assertEquals(1.40, lineas[productoId]!!.coste, 1e-9)
            assertEquals(0.0, lineas[sinCoste]!!.coste, 1e-9)
            assertEquals(0, consumo(recepciones[0]))
            assertEquals(0, db.query("SELECT COUNT(*) FROM consumos_capa", null).use { it.moveToFirst(); it.getInt(0) })
        } finally {
            archivo?.delete()
        }
    }
}
