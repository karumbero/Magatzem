package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Una retirada de artículos (pantalla oculta del sol → Retirar): se anotan los artículos y cantidades retirados, valorados a PVP, y al
 * finalizar se guarda con su fecha y se descuenta de la existencia. No es una venta: no hay beneficio ni capas de coste.
 */
@Entity(tableName = "retiradas", indices = [Index(value = ["uuid"], unique = true)])
data class RetiradaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    /** "yyyy-MM-dd HH:mm:ss" */
    val fecha: String,
    val total: Double
)

/** Línea de una retirada: instantánea del artículo (SKU, nombre y PVP del momento), cantidad e importe (cantidad × PVP). */
@Entity(
    tableName = "retirada_lineas",
    foreignKeys = [
        ForeignKey(entity = RetiradaEntity::class, parentColumns = ["id"], childColumns = ["retiradaId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["retiradaId"])]
)
data class RetiradaLineaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val retiradaId: Long,
    val productoId: Long?,
    val sku: String?,
    val nombre: String,
    val cantidad: Int,
    val pvp: Double,
    val importe: Double
)

@Dao
interface RetiradaDao {
    @Insert
    suspend fun insertar(retirada: RetiradaEntity): Long

    @Insert
    suspend fun insertarLineas(lineas: List<RetiradaLineaEntity>)

    /** Guarda la retirada con todas sus líneas, o nada. */
    @Transaction
    suspend fun guardar(retirada: RetiradaEntity, lineas: List<RetiradaLineaEntity>): Long {
        val id = insertar(retirada)
        insertarLineas(lineas.map { it.copy(retiradaId = id) })
        return id
    }

    @Query("SELECT * FROM retiradas ORDER BY fecha DESC, id DESC")
    fun observeAll(): Flow<List<RetiradaEntity>>

    @Query("SELECT * FROM retirada_lineas WHERE retiradaId = :retiradaId ORDER BY id")
    suspend fun lineasDe(retiradaId: Long): List<RetiradaLineaEntity>
}
