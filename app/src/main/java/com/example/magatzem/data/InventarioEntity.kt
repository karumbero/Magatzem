package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.util.UUID
import kotlinx.coroutines.flow.Flow

const val INVENTARIO_ABIERTO = "abierto"
const val INVENTARIO_CERRADO = "cerrado"

/**
 * Un inventario (recuento físico de la tienda). Se guarda entero y se puede abrir y retomar cuando se quiera, porque suele durar
 * mucho tiempo. Al crearlo se anotan todos los artículos (ver [InventarioLineaEntity]).
 */
@Entity(tableName = "inventarios", indices = [Index(value = ["uuid"], unique = true)])
data class InventarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val nombre: String,
    /** Cuándo se creó ("yyyy-MM-dd HH:mm:ss"). */
    val fecha: String,
    val estado: String = INVENTARIO_ABIERTO,
    val fechaCierre: String? = null
)

/**
 * Una fila del inventario: un artículo con lo que había al crearlo y lo que se va contando. SKU, nombre, categoría y proveedor se copian
 * para que el inventario siga valiendo aunque luego cambien. `existenciaAlContar` es la existencia del artículo en el momento de
 * contarlo: así, aunque se siga vendiendo durante el inventario, la diferencia compara lo contado con lo que debía haber entonces.
 */
@Entity(
    tableName = "inventario_lineas",
    foreignKeys = [
        ForeignKey(
            entity = InventarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventarioId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["inventarioId", "productoId"], unique = true)]
)
data class InventarioLineaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inventarioId: Long,
    val productoId: Long,
    val sku: String?,
    val referencia: String?,
    val codigoBarras: String?,
    val nombre: String,
    val categoria: String?,
    val proveedor: String?,
    /** Existencia al crear el inventario. */
    val existenciaInicial: Int,
    /** Unidades dadas de baja por incidencias (acumuladas hasta crear el inventario). */
    val bajas: Int,
    /** Unidades contadas; null = todavía sin contar. */
    val contadas: Int? = null,
    /** Existencia del artículo cuando se contó (la última vez que se tocó el recuento). */
    val existenciaAlContar: Int? = null,
    val fechaConteo: String? = null
)

/** Un inventario con su progreso, para el listado. */
data class InventarioResumen(
    @Embedded val inventario: InventarioEntity,
    val total: Int,
    val contados: Int
)

data class BajasPorProducto(val productoId: Long, val bajas: Int)

@Dao
interface InventarioDao {
    @Query(
        "SELECT i.*, (SELECT COUNT(*) FROM inventario_lineas l WHERE l.inventarioId = i.id AND (l.existenciaInicial != 0 OR l.bajas > 0 OR l.contadas IS NOT NULL)) AS total, " +
            "(SELECT COUNT(*) FROM inventario_lineas l WHERE l.inventarioId = i.id AND l.contadas IS NOT NULL) AS contados " +
            "FROM inventarios i ORDER BY i.fecha DESC, i.id DESC"
    )
    fun observeResumenes(): Flow<List<InventarioResumen>>

    @Query("SELECT * FROM inventarios WHERE id = :id")
    fun observeInventario(id: Long): Flow<InventarioEntity?>

    @Query("SELECT * FROM inventario_lineas WHERE inventarioId = :id ORDER BY categoria, nombre, id")
    fun observeLineas(id: Long): Flow<List<InventarioLineaEntity>>

    @Query("SELECT * FROM inventario_lineas WHERE inventarioId = :id ORDER BY categoria, nombre, id")
    suspend fun lineas(id: Long): List<InventarioLineaEntity>

    @Insert
    suspend fun insertar(inventario: InventarioEntity): Long

    @Insert
    suspend fun insertarLineas(lineas: List<InventarioLineaEntity>)

    @Query("UPDATE inventario_lineas SET contadas = :contadas, existenciaAlContar = :existenciaAlContar, fechaConteo = :fecha WHERE id = :id")
    suspend fun contar(id: Long, contadas: Int?, existenciaAlContar: Int?, fecha: String?)

    @Query("UPDATE inventarios SET estado = :estado, fechaCierre = :fechaCierre WHERE id = :id")
    suspend fun cambiarEstado(id: Long, estado: String, fechaCierre: String?)

    @Query("DELETE FROM inventarios WHERE id = :id")
    suspend fun borrar(id: Long)

    @Query("SELECT productoId AS productoId, SUM(cantidad) AS bajas FROM incidencias WHERE productoId IS NOT NULL GROUP BY productoId")
    suspend fun bajasPorProducto(): List<BajasPorProducto>
}
