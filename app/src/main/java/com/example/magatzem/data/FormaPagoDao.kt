package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FormaPagoDao {
    @Query("SELECT * FROM formas_pago ORDER BY nombre COLLATE NOCASE")
    fun observeAll(): Flow<List<FormaPagoEntity>>

    @Query("SELECT * FROM formas_pago ORDER BY nombre COLLATE NOCASE")
    suspend fun obtenerTodas(): List<FormaPagoEntity>

    /** Solo las activas, para ofrecerlas al elegir cómo pagar algo nuevo. */
    @Query("SELECT * FROM formas_pago WHERE activo = 1 ORDER BY nombre COLLATE NOCASE")
    fun observeActivas(): Flow<List<FormaPagoEntity>>

    @Insert
    suspend fun insert(formaPago: FormaPagoEntity): Long

    /**
     * Para sembrar Contado/Transferencia de forma segura si hiciera falta reponerlas (índice único
     * por nombre + ignorar conflicto): no hace nada si ya existen.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiNoExiste(formaPago: FormaPagoEntity)

    @Update
    suspend fun update(formaPago: FormaPagoEntity)

    @Delete
    suspend fun delete(formaPago: FormaPagoEntity)

    /** Marca [id] como el banco principal y desmarca el resto. */
    @Query("UPDATE formas_pago SET principal = (id = :id)")
    suspend fun marcarPrincipal(id: Long)
}
