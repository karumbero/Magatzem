package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Update
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecepcionDao {
    @Insert
    suspend fun insertarRecepcion(recepcion: RecepcionEntity): Long

    @Insert
    suspend fun insertarLineas(lineas: List<RecepcionLineaEntity>)

    @Insert
    suspend fun insertarAlbaran(albaran: AlbaranEntity): Long

    @Insert
    suspend fun insertarFactura(factura: FacturaEntity): Long

    @Query("SELECT COUNT(*) FROM recepciones")
    fun observeCantidadRecepciones(): Flow<Int>

    @Query("SELECT COUNT(*) FROM albaranes")
    fun observeCantidadAlbaranes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM facturas")
    fun observeCantidadFacturas(): Flow<Int>

    @Query("SELECT COUNT(*) FROM facturas WHERE estado = 'pendiente'")
    fun observeCantidadFacturasPendientes(): Flow<Int>

    // Primera exportación (apertura): se vacía el histórico de Entradas. Albaranes y facturas se
    // borran a mano porque su FK a la recepción es SET_NULL (no CASCADE); las líneas sí caen solas,
    // y los pagos de cada factura también (FK en cascada).
    @Query("DELETE FROM albaranes")
    suspend fun borrarAlbaranes()

    @Query("DELETE FROM facturas")
    suspend fun borrarFacturas()

    @Query("DELETE FROM recepciones")
    suspend fun borrarRecepciones()

    // Oficina → Albaranes / Facturas
    @Query("SELECT * FROM recepciones")
    fun observeRecepciones(): Flow<List<RecepcionEntity>>

    @Query("SELECT * FROM albaranes")
    fun observeAlbaranes(): Flow<List<AlbaranEntity>>

    @Query("SELECT * FROM facturas")
    suspend fun obtenerFacturas(): List<FacturaEntity>

    @Query("SELECT * FROM facturas")
    fun observeFacturas(): Flow<List<FacturaEntity>>

    @Query("SELECT * FROM recepciones WHERE id = :id")
    suspend fun obtenerRecepcion(id: Long): RecepcionEntity?

    @Query("SELECT * FROM facturas WHERE id = :id")
    suspend fun obtenerFactura(id: Long): FacturaEntity?

    @Query("DELETE FROM albaranes WHERE id = :id")
    suspend fun borrarAlbaran(id: Long)

    @Update
    suspend fun actualizarFactura(factura: FacturaEntity)

    // Fusión de documentos con el mismo proveedor y el mismo número (se comparan sin mayúsculas ni espacios).
    @Query(
        "SELECT a.* FROM albaranes a JOIN recepciones r ON a.recepcionId = r.id " +
            "WHERE r.proveedorId = :proveedorId AND lower(trim(a.numero)) = lower(trim(:numero)) LIMIT 1"
    )
    suspend fun buscarAlbaran(proveedorId: Long, numero: String): AlbaranEntity?

    @Query(
        "SELECT f.* FROM facturas f JOIN recepciones r ON f.recepcionId = r.id " +
            "WHERE r.proveedorId = :proveedorId AND lower(trim(f.numero)) = lower(trim(:numero)) LIMIT 1"
    )
    suspend fun buscarFactura(proveedorId: Long, numero: String): FacturaEntity?

    @Update
    suspend fun actualizarAlbaran(albaran: AlbaranEntity)

    @Query("SELECT * FROM albaranes WHERE id = :id")
    suspend fun obtenerAlbaran(id: Long): AlbaranEntity?

    @Query("SELECT * FROM recepcion_lineas WHERE recepcionId = :recepcionId ORDER BY id")
    suspend fun obtenerLineas(recepcionId: Long): List<RecepcionLineaEntity>

    @Query("DELETE FROM recepcion_lineas WHERE recepcionId = :recepcionId")
    suspend fun eliminarLineas(recepcionId: Long)

    @Update
    suspend fun actualizarRecepcion(recepcion: RecepcionEntity)

    @Query("UPDATE recepcion_lineas SET recepcionId = :hasta WHERE recepcionId = :desde")
    suspend fun moverLineas(desde: Long, hasta: Long)

    @Query("DELETE FROM recepciones WHERE id = :id")
    suspend fun borrarRecepcion(id: Long)

    // Pantallas Albarán / Factura de Movimientos (edición con guardado automático)
    @Query("SELECT * FROM albaranes WHERE recepcionId = :recepcionId LIMIT 1")
    suspend fun buscarAlbaranPorRecepcion(recepcionId: Long): AlbaranEntity?

    @Query("SELECT * FROM facturas WHERE recepcionId = :recepcionId LIMIT 1")
    suspend fun buscarFacturaPorRecepcion(recepcionId: Long): FacturaEntity?

    @Query("DELETE FROM recepcion_lineas WHERE recepcionId = :recepcionId AND productoId = :productoId")
    suspend fun eliminarLineasDeProducto(recepcionId: Long, productoId: Long)

    @Query("DELETE FROM facturas WHERE id = :id")
    suspend fun borrarFactura(id: Long)
}
