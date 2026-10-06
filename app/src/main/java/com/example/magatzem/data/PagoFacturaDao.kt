package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PagoFacturaDao {
    @Query("SELECT * FROM pagos_factura ORDER BY facturaId, numeroPlazo")
    fun observeAll(): Flow<List<PagoFacturaEntity>>

    @Query("SELECT * FROM pagos_factura WHERE facturaId = :facturaId ORDER BY numeroPlazo")
    suspend fun obtenerPorFactura(facturaId: Long): List<PagoFacturaEntity>

    @Insert
    suspend fun insertar(pagos: List<PagoFacturaEntity>)

    @Insert
    suspend fun insertarUno(pago: PagoFacturaEntity): Long

    @Update
    suspend fun actualizar(pago: PagoFacturaEntity)

    @Query("DELETE FROM pagos_factura WHERE facturaId = :facturaId")
    suspend fun eliminarPorFactura(facturaId: Long)
}
