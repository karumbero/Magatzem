package com.example.magatzem.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserFacturaTest {
    private val proveedores = listOf(
        ProvInfo(16, "Ceramica Pons", "B07123456"),
        ProvInfo(4, "Edicions Mar", "B07999999")
    )
    private val productos = listOf(
        ProdInfo(1, "16/1901844", "1901844", "Imán gato flores", 16),
        ProdInfo(2, "16/1901856", "1901856", "Imán barco azul", 16),
        ProdInfo(3, "4/PC-001", "PC-001", "Postal Cala Ratjada", 4)
    )
    private val factura = listOf(
        "CERAMICA PONS S.L.",
        "CIF: B-07123456",
        "Calle del Mar 12 Palma",
        "FACTURA Nº A-2026-0561",
        "Fecha: 03/10/2026   Vencimiento: 02/11/2026",
        "Cliente: Maria Llull Rosselló  NIF 42997614B",
        "Ref.      Descripcion          Cant  Precio   Dto  Importe",
        "1901844   IMAN GATO FLORES     100   1,60          160,00",
        "1901856   IMAN BARCO AZUL      48    1,60           76,80",
        "9999999   ARTICULO SIN ALTA     3    2,00            6,00",
        "Base imponible 242,80",
        "IVA 21%  50,99",
        "TOTAL FACTURA 293,79"
    )

    @Test fun leeProveedorNumeroFechaYTotales() {
        val r = ParserFactura.interpretar(factura, proveedores, "42997614B", productos)
        assertEquals(16L, r.proveedor?.id)
        assertEquals("A-2026-0561", r.numero)
        assertEquals("2026-10-03", r.fechaIso)
        assertEquals(293.79, r.total!!, 0.001)
        assertEquals(242.80, r.base!!, 0.001)
        assertEquals(50.99, r.iva!!, 0.001)
    }

    @Test fun reconoceLasLineasPorRefYComprueba() {
        val r = ParserFactura.interpretar(factura, proveedores, "42997614B", productos)
        assertEquals(2, r.lineas.size)
        val a = r.lineas.first { it.producto.id == 1L }
        assertEquals(100, a.cantidad); assertEquals(1.60, a.coste, 0.001); assertTrue(a.cuadra)
        val b = r.lineas.first { it.producto.id == 2L }
        assertEquals(48, b.cantidad); assertEquals(1.60, b.coste, 0.001)
        // La línea de un artículo que no existe va aparte, para añadirla a mano.
        assertTrue(r.sinReconocer.any { it.contains("9999999") })
    }

    @Test fun tolerasUnCaracterMalLeidoEnLaRef() {
        val f = listOf("CIF B07123456", "FACTURA 77001", "1901B44 IMAN GATO 10 1,60 16,00")  // 8 → B
        val r = ParserFactura.interpretar(f, proveedores, null, productos)
        assertEquals(1L, r.lineas.single().producto.id)
        assertEquals(10, r.lineas.single().cantidad)
    }

    @Test fun importesEspanoles() {
        assertEquals(1234.56, ParserFactura.numero("1.234,56")!!, 0.0001)
        assertEquals(12.5, ParserFactura.numero("12,50")!!, 0.0001)
        assertEquals(12.5, ParserFactura.numero("12.50")!!, 0.0001)
        assertEquals(7.0, ParserFactura.numero("7")!!, 0.0001)
        assertNull(ParserFactura.numero("ABC"))
    }

    @Test fun sinProveedorConocidoNoInventaNada() {
        val r = ParserFactura.interpretar(listOf("Algo raro", "sin datos"), proveedores, null, productos)
        assertNull(r.proveedor); assertNull(r.numero); assertNull(r.fechaIso); assertTrue(r.lineas.isEmpty())
    }
}
