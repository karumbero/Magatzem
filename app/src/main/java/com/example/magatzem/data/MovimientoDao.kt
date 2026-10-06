package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimientoDao {
    @Insert
    suspend fun insertarCaja(movimiento: MovimientoCajaEntity)

    @Insert
    suspend fun insertarBanco(movimiento: MovimientoBancoEntity)

    /** null = todavía no hay ningún movimiento de caja. */
    @Query("SELECT SUM(importe) FROM movimientos_caja")
    suspend fun saldoCaja(): Double?

    @Query("SELECT COUNT(*) FROM movimientos_caja")
    suspend fun contarMovimientosCaja(): Int

    @Query("SELECT * FROM movimientos_caja ORDER BY fecha DESC, id DESC")
    fun observeCaja(): Flow<List<MovimientoCajaEntity>>

    @Query("SELECT * FROM movimientos_banco ORDER BY fecha DESC, id DESC")
    fun observeBanco(): Flow<List<MovimientoBancoEntity>>

    @Query("SELECT * FROM movimientos_banco WHERE pagoId = :pagoId LIMIT 1")
    suspend fun bancoDePago(pagoId: Long): MovimientoBancoEntity?

    @androidx.room.Update
    suspend fun actualizarBanco(movimiento: MovimientoBancoEntity)

    @Query("DELETE FROM movimientos_banco WHERE pagoId = :pagoId")
    suspend fun borrarBancoDePago(pagoId: Long)
}
