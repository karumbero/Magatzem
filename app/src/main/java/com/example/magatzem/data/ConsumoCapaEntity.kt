package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

const val CONSUMO_VENTA = "venta"
const val CONSUMO_INCIDENCIA = "incidencia"

/**
 * Capas de coste (FIFO). Una capa es la línea de un artículo en una entrada de mercancía (`recepcion_lineas`): cuántas unidades
 * entraron y a qué coste. Esta tabla anota cuántas unidades de esa capa se han gastado — por una venta importada de MiTPV o por una
 * incidencia — de modo que lo que queda de una capa es sus unidades menos lo anotado aquí. Se identifica por entrada y artículo, no
 * por el id de la línea, porque las líneas se reescriben al editar el documento.
 *
 * Una devolución resta de aquí lo que el ticket original había anotado, así las unidades vuelven a la misma capa.
 */
@Entity(
    tableName = "consumos_capa",
    foreignKeys = [
        ForeignKey(
            entity = RecepcionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recepcionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recepcionId", "productoId"]), Index(value = ["ventaLineaId"])]
)
data class ConsumoCapaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recepcionId: Long,
    val productoId: Long,
    /** Unidades gastadas de la capa (siempre positivo; una devolución borra o reduce el consumo del ticket original). */
    val cantidad: Int,
    /** [CONSUMO_VENTA] o [CONSUMO_INCIDENCIA]. */
    val tipo: String,
    val ventaLineaId: Long? = null,
    val incidenciaId: Long? = null,
    val fecha: String
)

/** Capa tal como sale de la base de datos (antes de restar lo gastado). */
data class CapaBruta(val recepcionId: Long, val cantidad: Int, val coste: Double)

data class ConsumoAgrupado(val recepcionId: Long, val cantidad: Int)

@Dao
interface ConsumoCapaDao {
    @Insert
    suspend fun insertar(consumo: ConsumoCapaEntity): Long

    /** Unidades de la capa (entrada + artículo) ya gastadas. */
    @Query("SELECT COALESCE(SUM(cantidad), 0) FROM consumos_capa WHERE recepcionId = :recepcionId AND productoId = :productoId")
    suspend fun consumido(recepcionId: Long, productoId: Long): Int

    /** Las capas de un artículo de más antigua a más reciente (por fecha de la entrada); varias líneas de una misma entrada, a coste medio. */
    @Query(
        "SELECT l.recepcionId AS recepcionId, SUM(l.cantidad) AS cantidad, " +
            "CASE WHEN SUM(l.cantidad) > 0 THEN SUM(l.cantidad * l.coste) / SUM(l.cantidad) ELSE 0 END AS coste " +
            "FROM recepcion_lineas l JOIN recepciones r ON r.id = l.recepcionId WHERE l.productoId = :productoId " +
            "GROUP BY l.recepcionId ORDER BY r.fecha, r.id"
    )
    suspend fun capas(productoId: Long): List<CapaBruta>

    @Query("SELECT recepcionId AS recepcionId, SUM(cantidad) AS cantidad FROM consumos_capa WHERE productoId = :productoId GROUP BY recepcionId")
    suspend fun consumidoPorRecepcion(productoId: Long): List<ConsumoAgrupado>

    @Query("SELECT * FROM consumos_capa WHERE ventaLineaId = :ventaLineaId ORDER BY id")
    suspend fun consumosDeLinea(ventaLineaId: Long): List<ConsumoCapaEntity>

    @Query("DELETE FROM consumos_capa WHERE incidenciaId = :incidenciaId")
    suspend fun eliminarDeIncidencia(incidenciaId: Long)

    @Query("UPDATE consumos_capa SET cantidad = :cantidad WHERE id = :id")
    suspend fun cambiarCantidad(id: Long, cantidad: Int)

    @Query("DELETE FROM consumos_capa WHERE id = :id")
    suspend fun eliminar(id: Long)

    /** Lo que queda sin gastar de cada capa del artículo, de más antigua a más reciente (FIFO). */
    suspend fun capasDisponibles(productoId: Long): List<CapaDisponible> {
        val gastado = consumidoPorRecepcion(productoId).associate { it.recepcionId to it.cantidad }
        return capas(productoId).map { CapaDisponible(it.recepcionId, it.cantidad - (gastado[it.recepcionId] ?: 0), it.coste) }
    }
}
