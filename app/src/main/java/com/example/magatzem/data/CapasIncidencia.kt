package com.example.magatzem.data

/**
 * Capas de coste y incidencias. Una incidencia (rotura, robo, falta en entrada) gasta unidades de las capas más antiguas del artículo y
 * se queda con su coste real; recuperarla (o cambiar sus unidades) las devuelve a las capas. Siempre dentro de la transacción de quien
 * llama, junto con el cambio de existencia.
 */
object CapasIncidencia {
    /**
     * Gasta [cantidad] unidades FIFO para la incidencia [incidenciaId] y devuelve el coste base unitario medio de lo gastado; las
     * unidades sin capa se valoran a [costeSinCapa] (el coste actual del artículo).
     */
    suspend fun gastar(db: AppDatabase, productoId: Long, cantidad: Int, incidenciaId: Long, fecha: String, costeSinCapa: Double): Double {
        val dao = db.consumoCapaDao()
        val resultado = CapasFifo.consumir(dao.capasDisponibles(productoId), cantidad)
        resultado.partes.forEach { p ->
            dao.insertar(
                ConsumoCapaEntity(
                    recepcionId = p.recepcionId, productoId = productoId, cantidad = p.cantidad,
                    tipo = CONSUMO_INCIDENCIA, incidenciaId = incidenciaId, fecha = fecha
                )
            )
        }
        return resultado.costeMedio(costeSinCapa)
    }

    /** Devuelve a sus capas las unidades que gastó la incidencia. */
    suspend fun liberar(db: AppDatabase, incidenciaId: Long) {
        db.consumoCapaDao().eliminarDeIncidencia(incidenciaId)
    }
}
