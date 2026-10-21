package com.example.magatzem

import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.ui.entradas.LineaEntrada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineaPesoTest {
    private val p = ProductoEntity(nombre = "Jabón", fechaCreacion = "", fechaActualizacion = "")

    @Test fun porPeso_baseTotalSeConvierteAPrecioPorGramo() {
        val l = LineaEntrada(p, coste = "300", margen = "0", cantidad = "1000", porPeso = true)
        assertEquals(0.3, l.costeUnitario()!!, 1e-9)
        assertEquals(300.0, l.importe(), 1e-9)
    }

    @Test fun porPeso_sinGramosNoHayPrecio() {
        assertNull(LineaEntrada(p, "300", "0", "", porPeso = true).costeUnitario())
    }

    @Test fun normal_sigueSiendoCantidadPorPrecio() {
        val l = LineaEntrada(p, coste = "2,5", margen = "0", cantidad = "4")
        assertEquals(2.5, l.costeUnitario()!!, 1e-9)
        assertEquals(10.0, l.importe(), 1e-9)
    }
}
