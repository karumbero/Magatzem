package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categorias ORDER BY nombre COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM categorias ORDER BY nombre COLLATE NOCASE")
    suspend fun obtenerTodas(): List<CategoriaEntity>

    @Query("SELECT COUNT(*) FROM categorias WHERE parentId = :categoriaId")
    suspend fun contarHijas(categoriaId: Long): Int

    @Insert
    suspend fun insert(categoria: CategoriaEntity): Long

    @Update
    suspend fun update(categoria: CategoriaEntity)

    @Delete
    suspend fun delete(categoria: CategoriaEntity)
}
