package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportacionCatalogoDao {
    @Insert
    suspend fun insertar(exportacion: ExportacionCatalogoEntity): Long

    @Query("SELECT * FROM exportaciones_catalogo ORDER BY id DESC LIMIT 1")
    fun observeUltima(): Flow<ExportacionCatalogoEntity?>

    @Query("SELECT * FROM exportaciones_catalogo ORDER BY id DESC LIMIT 1")
    suspend fun obtenerUltima(): ExportacionCatalogoEntity?

    @Query("SELECT COUNT(*) FROM exportaciones_catalogo")
    suspend fun contar(): Int
}
