package com.example.magatzem

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.data.escaneo.ImportacionEscaneo
import com.example.magatzem.ui.documentos.TipoDoc
import com.example.magatzem.ui.escaneo.EstadoLinea
import com.example.magatzem.ui.escaneo.ImportarEscaneoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Importar lo que manda Escaneo: comprobación por referencia, producto nuevo con existencia 0, coste más alto con su PVP y entrada con plazos. */
@RunWith(AndroidJUnit4::class)
class ImportarEscaneoTest {
    private fun esperar(maxMs: Long = 8000, condicion: () -> Boolean) = runBlocking {
        var t = 0L
        while (!condicion() && t < maxMs) { delay(50); t += 50 }
        assertTrue("no ha terminado a tiempo", condicion())
    }

    private val json = """{"tipo":"factura","proveedor":"Prov Escaneo ${System.nanoTime()}","numero":"E-${System.nanoTime()}","fecha":"2026-10-23","base":58.0,"iva":12.18,"re":3.02,"total":73.2,
        "formaPago":"Banca March","plazos":[{"fecha":"2026-11-22","importe":36.6},{"fecha":"2026-12-22","importe":36.6}],
        "lineas":[{"ref":"A1","descripcion":"Taza","cantidad":10,"precio":2.0,"importe":20.0},{"ref":"B2","descripcion":"Mug nuevo","cantidad":10,"precio":3.8,"importe":38.0}]}"""

    @Test
    fun laFacturaDeEscaneoEntraEnMagatzem() {
        val app = ApplicationProvider.getApplicationContext<Application>() as MagatzemApplication
        val db = app.database
        val dto = JSONObject(json)
        val f = ImportacionEscaneo.leer(dto)
        assertEquals(2, f.lineas.size); assertEquals(2, f.plazos.size); assertEquals("Banca March", f.formaPago)

        runBlocking {
            val prov = db.proveedorDao().insert(ProveedorEntity(nombre = f.proveedor))
            val cat = db.categoriaDao().insert(CategoriaEntity(nombre = "Cat ${System.nanoTime()}"))
            val ahora = "2026-10-23 10:00:00"
            // A1 existe con coste 1,80 (margen 100 %, PVP 4,54, 5 uds): llega a 2,00 → coste más alto.
            db.productoDao().insert(ProductoEntity(sku = "$prov/A1", referenciaFabricante = "A1", nombre = "Taza", proveedorId = prov, coste = 1.8, margenBeneficio = 100.0, precioVenta = 4.54, existencia = 5, fechaCreacion = ahora, fechaActualizacion = ahora))
            if (db.formaPagoDao().obtenerTodas().none { it.nombre == "Banca March" }) db.formaPagoDao().insert(com.example.magatzem.data.FormaPagoEntity(nombre = "Banca March"))
            val vm = ImportarEscaneoViewModel(app)
            vm.preparar(f)
            esperar { vm.lineas.all { it.estado != EstadoLinea.SIN_COMPROBAR } }
            assertEquals(EstadoLinea.COSTE_MAS_ALTO, vm.lineas[0].estado)
            assertEquals(Math.round(2.0 * 1.262 * 2 * 100) / 100.0, vm.lineas[0].pvpRecomendado!!, 0.006)   // coste nuevo + el margen que ya tenía
            assertEquals(EstadoLinea.NO_EXISTE, vm.lineas[1].estado)

            // Proveedor sin configurar: quien no es de nivel 1 solo recibe el aviso.
            assertTrue(vm.avisoProveedor!!.contains("Toni"))
            vm.darEntrada(f)
            assertTrue(vm.mensaje!!.contains("contacta con Toni"))
            vm.puedeProbar = true
            vm.mensaje = null

            // Sin el producto no se da entrada.
            vm.darEntrada(f)
            esperar { vm.mensaje?.contains("Faltan productos") == true }

            // Se añade el producto que falta: existencia 0, con su categoría.
            vm.anadirProducto(f, "Mug nuevo", "$prov/B2", "B2", cat, 3.8, 0.0, 0.0)
            esperar { vm.lineas[1].estado == EstadoLinea.EXISTE }
            assertEquals(0, vm.lineas[1].producto!!.existencia)
            assertEquals(cat, vm.lineas[1].producto!!.categoriaId)

            vm.darEntrada(f)
            esperar(15000) { vm.resultado != null }
            val taza = db.productoDao().obtenerPorId(vm.lineas[0].producto!!.id)!!
            assertEquals(15, taza.existencia)                 // 5 + 10
            assertEquals(2.0, taza.coste, 0.0001)
            assertEquals(vm.lineas[0].pvpRecomendado!!, taza.precioVenta, 0.006)   // PVP actualizado
            assertEquals(10, db.productoDao().obtenerPorId(vm.lineas[1].producto!!.id)!!.existencia)
            val factura = db.recepcionDao().buscarFactura(prov, f.numero)
            assertNotNull(factura)
            val pagos = db.pagoFacturaDao().obtenerPorFactura(factura!!.id)
            assertEquals(2, pagos.size)
            assertEquals(listOf("2026-11-22", "2026-12-22"), pagos.map { it.fechaPrevista })
            assertTrue(pagos.all { it.fechaPago == null && it.formaPagoId != null })
            // La entrada con éxito deja al proveedor marcado como configurado.
            assertTrue(db.proveedorDao().obtenerTodos().first { it.id == prov }.facturasPdf)
            // Segundo intento: no se duplica.
            vm.darEntrada(f)
            esperar { vm.mensaje?.contains("Ya existe") == true }
            vm.cerrar()
        }
    }

    /** Sellos de Sermoga: proveedor exento, líneas a precio de lista y descuento comercial aparte (no cambia coste ni PVP). */
    @Test
    fun elDescuentoDelDocumentoNoCambiaElCosteNiElPvp() {
        val app = ApplicationProvider.getApplicationContext<Application>() as MagatzemApplication
        val db = app.database
        val nombre = "Sellos ${System.nanoTime()}"
        val f = ImportacionEscaneo.leer(JSONObject("""{"tipo":"factura","proveedor":"$nombre","numero":"SV-${System.nanoTime()}","fecha":"2026-10-23","total":86.0,"descuentoPct":14.0,
            "lineas":[{"ref":"selloB","descripcion":"Sellos B","cantidad":50,"precio":2.0,"importe":100.0}]}"""))
        runBlocking {
            val prov = db.proveedorDao().insert(ProveedorEntity(nombre = nombre, exentoIva = true))
            val ahora = "2026-10-23 10:00:00"
            db.productoDao().insert(ProductoEntity(sku = "$prov/selloB", referenciaFabricante = "selloB", nombre = "Sellos B", proveedorId = prov, coste = 2.0, margenBeneficio = 0.0, precioVenta = 2.0, existencia = 0, fechaCreacion = ahora, fechaActualizacion = ahora))
            val vm = ImportarEscaneoViewModel(app)
            vm.puedeProbar = true
            vm.preparar(f)
            esperar { vm.lineas.all { it.estado != EstadoLinea.SIN_COMPROBAR } }
            vm.darEntrada(f)
            esperar { vm.resultado != null }
            val p = db.productoDao().obtenerPorId(vm.lineas[0].producto!!.id)!!
            assertEquals(50, p.existencia)
            assertEquals(2.0, p.coste, 0.0001); assertEquals(2.0, p.precioVenta, 0.006)   // el descuento es beneficio
            val rec = db.recepcionDao().buscarFactura(prov, f.numero)!!
            assertEquals(86.0, rec.base + rec.iva, 0.01)   // lo que se paga: 100 − 14 %
            vm.cerrar()
        }
    }
}
