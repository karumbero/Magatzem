package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios ORDER BY nombre COLLATE NOCASE")
    fun observeAll(): Flow<List<UsuarioEntity>>

    @Query("SELECT * FROM usuarios ORDER BY nombre COLLATE NOCASE")
    suspend fun obtenerTodos(): List<UsuarioEntity>

    @Query("SELECT * FROM usuarios WHERE activo = 1 ORDER BY nombre COLLATE NOCASE")
    fun observeActivos(): Flow<List<UsuarioEntity>>

    @Query("SELECT * FROM usuarios WHERE uuid = :uuid")
    suspend fun obtenerPorUuid(uuid: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun obtenerPorId(id: Long): UsuarioEntity?

    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun contar(): Int

    @Insert
    suspend fun insert(usuario: UsuarioEntity): Long

    @Update
    suspend fun update(usuario: UsuarioEntity)

    @Delete
    suspend fun delete(usuario: UsuarioEntity)
}
