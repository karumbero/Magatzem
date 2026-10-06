package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Cajeros que han cobrado en MiTPV. Se rellena y actualiza con cada cierre importado (MiTPV manda todos los
 * cajeros que intervinieron en el turno), así los informes saben el nombre de quien cobró cada ticket aunque
 * ese usuario solo exista en MiTPV. Es independiente de Ajustes → Usuarios, que pueden ser distintos.
 */
@Entity(tableName = "cajeros", indices = [Index(value = ["uuid"], unique = true)])
data class CajeroEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val nombre: String,
    /** Fecha de cierre del último cierre importado en el que apareció. */
    val ultimaActividad: String
)

@Dao
interface CajeroDao {
    @Query("SELECT * FROM cajeros WHERE uuid = :uuid")
    suspend fun obtenerPorUuid(uuid: String): CajeroEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(cajero: CajeroEntity): Long

    @Query("SELECT * FROM cajeros ORDER BY nombre COLLATE NOCASE")
    fun observeAll(): Flow<List<CajeroEntity>>
}
