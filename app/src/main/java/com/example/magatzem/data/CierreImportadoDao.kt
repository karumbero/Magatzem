package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CierreImportadoDao {
    @Query("SELECT * FROM cierres_importados ORDER BY cerradoEn DESC")
    fun observeAll(): Flow<List<CierreImportadoEntity>>

    @Query("SELECT * FROM cierres_importados WHERE uuid = :uuid LIMIT 1")
    suspend fun obtenerPorUuid(uuid: String): CierreImportadoEntity?

    @Insert
    suspend fun insertarCierre(cierre: CierreImportadoEntity): Long

    @Insert
    suspend fun insertarRetirada(retirada: RetiradaImportadaEntity): Long

    /** Pagos a proveedor de MiTPV que todavía no se han podido aplicar a una factura. */
    @Query("SELECT * FROM retiradas_importadas WHERE tipo = 'PROVEEDOR' AND pagoFacturaId IS NULL")
    suspend fun retiradasProveedorSinAplicar(): List<RetiradaImportadaEntity>

    @Query("SELECT * FROM retiradas_importadas WHERE tipo = 'PROVEEDOR' AND pagoFacturaId IS NULL ORDER BY fecha DESC")
    fun observeRetiradasProveedorSinAplicar(): Flow<List<RetiradaImportadaEntity>>

    @Query("UPDATE retiradas_importadas SET pagoFacturaId = :pagoId WHERE id = :id")
    suspend fun marcarRetiradaAplicada(id: Long, pagoId: Long)

    @Insert
    suspend fun insertarVenta(venta: VentaImportadaEntity): Long

    @Insert
    suspend fun insertarLineas(lineas: List<VentaLineaImportadaEntity>)

    @Insert
    suspend fun insertarLinea(linea: VentaLineaImportadaEntity): Long

    @Query("SELECT * FROM ventas_importadas WHERE uuid = :uuid")
    suspend fun obtenerVentaPorUuid(uuid: String): VentaImportadaEntity?

    @Query("SELECT * FROM venta_lineas_importadas WHERE ventaId = :ventaId AND productoId = :productoId AND cantidad > 0 ORDER BY id")
    suspend fun lineasVendidasDe(ventaId: Long, productoId: Long): List<VentaLineaImportadaEntity>

    @Query("SELECT COUNT(*) FROM ventas_importadas WHERE uuid IN (:uuids)")
    suspend fun contarVentasPorUuid(uuids: List<String>): Int

    /** Cuántos artículos distintos tienen ventas importadas (base de la regla "productos con ventas"). */
    @Query("SELECT COUNT(DISTINCT productoId) FROM venta_lineas_importadas WHERE productoId IS NOT NULL")
    fun observeProductosConVentas(): Flow<Int>

    @Query("SELECT COUNT(*) FROM venta_lineas_importadas WHERE productoId = :productoId")
    suspend fun contarLineasDe(productoId: Long): Int
}
