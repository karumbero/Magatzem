package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidenciaDao {
    @Query("SELECT * FROM incidencias ORDER BY fecha DESC, id DESC")
    fun observeAll(): Flow<List<IncidenciaEntity>>

    @Insert
    suspend fun insertar(incidencia: IncidenciaEntity): Long

    @Update
    suspend fun actualizar(incidencia: IncidenciaEntity)

    @Delete
    suspend fun eliminar(incidencia: IncidenciaEntity)
}
