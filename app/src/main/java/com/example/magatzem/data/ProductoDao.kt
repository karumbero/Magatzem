package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos ORDER BY nombre COLLATE NOCASE")
    fun observeAll(): Flow<List<ProductoEntity>>

    @Query("SELECT COUNT(*) FROM productos")
    fun observeCantidadTotal(): Flow<Int>

    @Query("SELECT * FROM productos WHERE id = :id")
    suspend fun obtenerPorId(id: Long): ProductoEntity?

    @Query("SELECT * FROM productos WHERE uuid = :uuid")
    suspend fun obtenerPorUuid(uuid: String): ProductoEntity?

    /**
     * `excludeId` deja fuera la comprobación al propio artículo que se está editando. `sku` nulo
     * (sin SKU tecleado) no cuenta como coincidencia: dos artículos sin SKU no son "el mismo".
     */
    @Query(
        """
        SELECT * FROM productos
        WHERE id != :excludeId
          AND (
            (:sku IS NOT NULL AND sku = :sku)
            OR (:codigoBarras != '' AND codigoBarras = :codigoBarras)
            OR (:referenciaFabricante != '' AND referenciaFabricante = :referenciaFabricante)
          )
        LIMIT 1
        """
    )
    suspend fun buscarDuplicado(
        sku: String?,
        codigoBarras: String,
        referenciaFabricante: String,
        excludeId: Long = -1
    ): ProductoEntity?

    /** Entradas busca por código de barras, REF de fabricante o SKU (artículos que solo tienen SKU). */
    @Query(
        "SELECT * FROM productos WHERE codigoBarras = :query OR referenciaFabricante = :query OR sku = :query LIMIT 1"
    )
    suspend fun buscarPorCodigoBarrasOReferencia(query: String): ProductoEntity?

    /** Al confirmar una entrada: nuevo coste/margen/PVP y suma de la cantidad recibida a la existencia. */
    @Query(
        "UPDATE productos SET coste = :coste, margenBeneficio = :margen, precioVenta = :precioVenta, " +
            "existencia = existencia + :cantidad, fechaActualizacion = :fecha WHERE id = :productoId"
    )
    suspend fun registrarEntrada(productoId: Long, cantidad: Int, coste: Double, margen: Double, precioVenta: Double, fecha: String)

    /**
     * Ventas/devoluciones importadas de MiTPV: suma `delta` a la existencia. NO toca `fechaActualizacion`
     * a propósito: un movimiento de stock no es un cambio de catálogo y no debe aparecer como artículo
     * pendiente de exportar.
     */
    @Query("UPDATE productos SET existencia = existencia + :delta WHERE id = :productoId")
    suspend fun sumarExistencia(productoId: Long, delta: Int)

    @Query("SELECT COUNT(*) FROM productos WHERE categoriaId = :categoriaId")
    suspend fun contarPorCategoria(categoriaId: Long): Int

    @Query("SELECT COUNT(*) FROM productos WHERE proveedorId = :proveedorId")
    suspend fun contarPorProveedor(proveedorId: Long): Int

    /** Nuevos o modificados desde `fecha` (exclusive): lo que la exportación al programa de ventas
     * todavía no ha mandado. `fechaActualizacion` sirve para ambos casos: al crear un artículo se
     * pone igual que `fechaCreacion`, así que un alta reciente también cae aquí sin comprobar las dos. */
    @Query("SELECT * FROM productos WHERE fechaActualizacion > :fecha ORDER BY fechaActualizacion")
    suspend fun pendientesDesde(fecha: String): List<ProductoEntity>

    @Query("SELECT COUNT(*) FROM productos WHERE fechaActualizacion > :fecha")
    fun observeCantidadPendientesDesde(fecha: String): Flow<Int>

    @Query("SELECT uuid FROM productos")
    suspend fun obtenerUuids(): List<String>

    @Query("SELECT uuid FROM productos")
    fun observeUuids(): Flow<List<String>>

    @Query("SELECT * FROM productos ORDER BY fechaActualizacion")
    suspend fun obtenerTodosOrdenados(): List<ProductoEntity>

    @Insert
    suspend fun insert(producto: ProductoEntity): Long

    @Update
    suspend fun update(producto: ProductoEntity)

    @Delete
    suspend fun delete(producto: ProductoEntity)

    // Para decidir si un artículo se puede borrar (ver ProductoViewModel.eliminar).
    @Query("SELECT COUNT(*) FROM recepcion_lineas WHERE productoId = :id")
    suspend fun contarLineasRecepcion(id: Long): Int

    @Query("SELECT COUNT(*) FROM pedido_lineas WHERE productoId = :id")
    suspend fun contarLineasPedido(id: Long): Int

    @Query("SELECT COUNT(*) FROM incidencias WHERE productoId = :id")
    suspend fun contarIncidencias(id: Long): Int

    @Query("SELECT COUNT(*) FROM venta_lineas_importadas WHERE productoId = :id")
    suspend fun contarVentasImportadas(id: Long): Int
}
