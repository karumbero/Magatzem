package com.example.magatzem

import com.example.magatzem.data.CapaDisponible
import com.example.magatzem.data.CapasFifo
import org.junit.Assert.assertEquals
import org.junit.Test

class CapasFifoTest {
    private val capas = listOf(CapaDisponible(1, 4, 1.00), CapaDisponible(2, 6, 1.40))

    @Test
    fun gastaPrimeroLaCapaMasAntigua() {
        val r = CapasFifo.consumir(capas, 3)
        assertEquals(1, r.partes.size)
        assertEquals(1L, r.partes[0].recepcionId)
        assertEquals(3, r.partes[0].cantidad)
        assertEquals(0, r.sinCapa)
        assertEquals(1.00, r.costeMedio(9.99), 1e-9)
    }

    @Test
    fun cruzaDosCapasConCosteMedio() {
        val r = CapasFifo.consumir(capas, 5)
        assertEquals(listOf(4, 1), r.partes.map { it.cantidad })
        assertEquals((4 * 1.00 + 1 * 1.40) / 5, r.costeMedio(9.99), 1e-9)
    }

    @Test
    fun lasUnidadesSinCapaVanAlCosteActual() {
        val r = CapasFifo.consumir(capas, 12)
        assertEquals(10, r.partes.sumOf { it.cantidad })
        assertEquals(2, r.sinCapa)
        assertEquals((4 * 1.00 + 6 * 1.40 + 2 * 2.00) / 12, r.costeMedio(2.00), 1e-9)
    }

    @Test
    fun ignoraCapasYaGastadas() {
        val r = CapasFifo.consumir(listOf(CapaDisponible(1, 0, 1.00), CapaDisponible(2, 6, 1.40)), 2)
        assertEquals(listOf(2L), r.partes.map { it.recepcionId })
    }

    @Test
    fun sinCapasTodoVaAlCosteActual() {
        val r = CapasFifo.consumir(emptyList(), 3)
        assertEquals(3, r.sinCapa)
        assertEquals(2.00, r.costeMedio(2.00), 1e-9)
    }

    @Test
    fun unaCapaSinCosteSeValoraAlCosteActual() {
        // Capa inicial sin coste conocido (0): al gastarla se usa el coste actual del artículo (2,00).
        val r = CapasFifo.consumir(listOf(CapaDisponible(1, 5, 0.0), CapaDisponible(2, 5, 1.00)), 7)
        assertEquals((5 * 2.00 + 2 * 1.00) / 7, r.costeMedio(2.00), 1e-9)
    }
}
