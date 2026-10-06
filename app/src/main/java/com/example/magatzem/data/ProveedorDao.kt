package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProveedorDao {
    @Query("SELECT * FROM proveedores ORDER BY nombre COLLATE NOCASE")
    fun observeAll(): Flow<List<ProveedorEntity>>

    @Query("SELECT * FROM proveedores ORDER BY nombre COLLATE NOCASE")
    suspend fun obtenerTodos(): List<ProveedorEntity>

    @Insert
    suspend fun insert(proveedor: ProveedorEntity): Long

    @Update
    suspend fun update(proveedor: ProveedorEntity)

    @Delete
    suspend fun delete(proveedor: ProveedorEntity)
}
