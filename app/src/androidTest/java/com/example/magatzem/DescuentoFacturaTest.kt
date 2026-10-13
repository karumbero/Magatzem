package com.example.magatzem

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.magatzem.data.AppDatabase
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.data.factorCoste
import com.example.magatzem.ui.documentos.DocumentoMotor
import com.example.magatzem.ui.documentos.TipoDoc
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Descuento del proveedor en facturas, sobre una base de datos EN MEMORIA: el coste de cada línea es el precio de lista menos el % del
 * documento, y de ahí salen el coste del artículo, la capa de coste, y la base y el IVA de la factura.
 */
@RunWith(AndroidJUnit4::class)
class DescuentoFacturaTest {
    private lateinit var db: AppDatabase
    private lateinit var motor: DocumentoMotor
    private var proveedorId = 0L
    private var productoA = 0L
    private var productoB = 0L
    private val f = factorCoste(false)

    @Before
    fun preparar() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        motor = DocumentoMotor(db)
        proveedorId = db.proveedorDao().insert(ProveedorEntity(nombre = "Prov"))
        fun producto(sku: String) = ProductoEntity(
            sku = sku, nombre = "Art $sku", proveedorId = proveedorId, coste = 0.0, margenBeneficio = 50.0, precioVenta = 0.0,
            existencia = 0, fechaCreacion = "2026-10-01", fechaActualizacion = "2026-10-01"
        )
        productoA = db.productoDao().insert(producto("1/A"))
        productoB = db.productoDao().insert(producto("1/B"))
    }

    @After
    fun cerrar() {
        db.close()
    }

    private suspend fun nuevaFactura(): Pair<Long, Long> {
        val facturaId = motor.crear(TipoDoc.FACTURA, proveedorId, "F-1", "2026-10-12")
        return facturaId to motor.abrir(TipoDoc.FACTURA, facturaId)!!.recepcionId
    }

    @Test
    fun descuentoReduceElCosteNetoYLaBaseDeLaFactura() = runBlocking {
        val (facturaId, rec) = nuevaFactura()
        assertNull(motor.guardarLinea(rec, productoA, 5, 10.0).error)
        motor.cambiarDescuento(rec, 10.0)

        val linea = db.recepcionDao().obtenerLineas(rec).single()
        assertEquals(9.0, linea.coste, 1e-9)       // neto
        assertEquals(10.0, linea.costeLista, 1e-9) // lista
        assertEquals(10.0, db.recepcionDao().obtenerRecepcion(rec)!!.descuento, 1e-9)
        assertEquals(9.0, db.productoDao().obtenerPorId(productoA)!!.coste, 1e-9)
        assertEquals(5, db.productoDao().obtenerPorId(productoA)!!.existencia)
        val factura = db.recepcionDao().obtenerFactura(facturaId)!!
        assertEquals(45.0, factura.base, 0.005)
        assertEquals(45.0 * (f - 1), factura.iva, 0.005)
        // La capa FIFO lleva el coste neto.
        assertEquals(9.0, db.consumoCapaDao().capas(productoA).single().coste, 1e-9)
    }

    @Test
    fun quitarElDescuentoVuelveAlPrecioDeLista() = runBlocking {
        val (facturaId, rec) = nuevaFactura()
        motor.guardarLinea(rec, productoA, 5, 10.0)
        motor.cambiarDescuento(rec, 10.0)
        motor.cambiarDescuento(rec, 0.0)

        assertEquals(10.0, db.recepcionDao().obtenerLineas(rec).single().coste, 1e-9)
        assertEquals(10.0, db.productoDao().obtenerPorId(productoA)!!.coste, 1e-9)
        assertEquals(50.0, db.recepcionDao().obtenerFactura(facturaId)!!.base, 0.005)
    }

    @Test
    fun unaLineaNuevaConDescuentoActivoEntraYaConElNeto() = runBlocking {
        val (facturaId, rec) = nuevaFactura()
        motor.guardarLinea(rec, productoA, 5, 10.0)
        motor.cambiarDescuento(rec, 10.0)
        motor.guardarLinea(rec, productoB, 1, 20.0)

        val b = db.recepcionDao().obtenerLineas(rec).first { it.productoId == productoB }
        assertEquals(18.0, b.coste, 1e-9)
        assertEquals(20.0, b.costeLista, 1e-9)
        assertEquals(63.0, db.recepcionDao().obtenerFactura(facturaId)!!.base, 0.005)
        // Lo que se muestra al reabrir es el precio de lista, no el neto.
        assertEquals(10.0, motor.lineas(rec).first { it.producto.id == productoA }.coste, 1e-9)
    }

    @Test
    fun editarLaCantidadNoPierdeElDescuento() = runBlocking {
        val (_, rec) = nuevaFactura()
        motor.guardarLinea(rec, productoA, 5, 10.0)
        motor.cambiarDescuento(rec, 10.0)
        motor.guardarLinea(rec, productoA, 8, 10.0)

        val l = db.recepcionDao().obtenerLineas(rec).single()
        assertEquals(8, l.cantidad)
        assertEquals(9.0, l.coste, 1e-9)
        assertEquals(8, db.productoDao().obtenerPorId(productoA)!!.existencia)
    }

    @Test
    fun pasarAlbaranAFacturaConDescuento() = runBlocking {
        val albaranId = motor.crear(TipoDoc.ALBARAN, proveedorId, "A-1", "2026-10-12")
        val rec = motor.abrir(TipoDoc.ALBARAN, albaranId)!!.recepcionId
        motor.guardarLinea(rec, productoA, 10, 10.0)
        assertEquals(10.0, db.productoDao().obtenerPorId(productoA)!!.coste, 1e-9)

        val res = motor.albaranAFactura(albaranId, "F-9", "2026-10-13", fusionar = false, descuento = 20.0)!!

        assertEquals(8.0, db.recepcionDao().obtenerLineas(rec).single().coste, 1e-9)
        assertEquals(8.0, db.productoDao().obtenerPorId(productoA)!!.coste, 1e-9)
        assertEquals(10, db.productoDao().obtenerPorId(productoA)!!.existencia) // las existencias no se tocan
        assertEquals(80.0, db.recepcionDao().obtenerFactura(res.facturaId)!!.base, 0.005)
    }
}
