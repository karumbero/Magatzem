package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PedidoDao {
    /** Para el listado de Pedidos: todos, el más reciente primero. */
    @Query("SELECT * FROM pedidos ORDER BY fecha DESC")
    fun observeAll(): Flow<List<PedidoEntity>>

    @Query("SELECT * FROM pedidos WHERE id = :id")
    suspend fun obtenerPorId(id: Long): PedidoEntity?

    @Insert
    suspend fun insertarPedido(pedido: PedidoEntity): Long

    /** Borra el pedido; sus líneas se borran solas (ON DELETE CASCADE). */
    @Delete
    suspend fun eliminar(pedido: PedidoEntity)

    @Insert
    suspend fun insertarLineas(lineas: List<PedidoLineaEntity>)

    @Update
    suspend fun actualizar(pedido: PedidoEntity)

    @Query("DELETE FROM pedido_lineas WHERE pedidoId = :pedidoId")
    suspend fun eliminarLineas(pedidoId: Long)

    @Query("SELECT * FROM pedido_lineas WHERE pedidoId = :pedidoId")
    suspend fun obtenerLineas(pedidoId: Long): List<PedidoLineaEntity>

    /** Pedidos de un proveedor en un estado dado (Entradas ofrece los pendientes al elegirlo). */
    @Query("SELECT * FROM pedidos WHERE proveedorId = :proveedorId AND estado = :estado ORDER BY fecha DESC")
    suspend fun obtenerPorProveedorYEstado(proveedorId: Long, estado: String): List<PedidoEntity>

    /** Borra un pedido por id: al confirmar una entrada hecha a partir de él, ya está atendido. */
    @Query("DELETE FROM pedidos WHERE id = :id")
    suspend fun eliminarPorId(id: Long)
}
