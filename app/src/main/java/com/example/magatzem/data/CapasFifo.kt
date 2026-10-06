package com.example.magatzem.data

/** Lo que queda de una capa de coste (una línea de entrada de un artículo): unidades sin gastar y su coste base unitario. */
data class CapaDisponible(val recepcionId: Long, val restante: Int, val coste: Double)

/** Unidades tomadas de una capa ([coste] 0 = capa sin coste conocido). */
data class ParteConsumo(val recepcionId: Long, val cantidad: Int, val coste: Double)

/**
 * Resultado de gastar unidades por orden de llegada: de qué capas salieron y cuántas unidades no tenían capa (existencia sin
 * entrada que la respalde: se valoran al coste actual del artículo, la "capa 0").
 */
data class ResultadoConsumo(val partes: List<ParteConsumo>, val sinCapa: Int) {
    /** Coste base unitario medio de lo gastado: las partes a su coste y lo que no tenía capa a [costeSinCapa]. */
    fun costeMedio(costeSinCapa: Double): Double {
        val unidades = partes.sumOf { it.cantidad } + sinCapa
        if (unidades == 0) return costeSinCapa
        // Una capa sin coste (existencia inicial sin precio real) se valora al coste actual del artículo, si ya se le ha puesto uno.
        return (partes.sumOf { it.cantidad * (if (it.coste > 0.0) it.coste else costeSinCapa) } + sinCapa * costeSinCapa) / unidades
    }
}

/** Reparto FIFO (primero lo más antiguo). [capas] ya viene ordenada de más antigua a más reciente. */
object CapasFifo {
    fun consumir(capas: List<CapaDisponible>, cantidad: Int): ResultadoConsumo {
        var pendiente = cantidad
        val partes = mutableListOf<ParteConsumo>()
        for (capa in capas) {
            if (pendiente <= 0) break
            if (capa.restante <= 0) continue
            val toma = minOf(capa.restante, pendiente)
            partes += ParteConsumo(capa.recepcionId, toma, capa.coste)
            pendiente -= toma
        }
        return ResultadoConsumo(partes, pendiente.coerceAtLeast(0))
    }
}
