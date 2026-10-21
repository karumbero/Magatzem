package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Descuento comercial de un proveedor en una factura: es un beneficio por proveedor (no cambia el coste ni el PVP de los artículos). Una fila por documento
 * con descuento; `importe` es el descuento sobre la base (sin IVA) y `importeConIva` el mismo con el IVA y recargo del proveedor.
 */
@Entity(tableName = "descuentos_comerciales", indices = [Index(value = ["recepcionId"], unique = true)])
data class DescuentoComercialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recepcionId: Long,
    val proveedorId: Long?,
    val fecha: String,
    val porcentaje: Double,
    val baseBruta: Double,
    val importe: Double,
    val importeConIva: Double
)

@Dao
interface DescuentoComercialDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(d: DescuentoComercialEntity)

    @Query("DELETE FROM descuentos_comerciales WHERE recepcionId = :recepcionId")
    suspend fun borrarDe(recepcionId: Long)

    @Query("SELECT * FROM descuentos_comerciales ORDER BY fecha DESC, id DESC")
    suspend fun todos(): List<DescuentoComercialEntity>
}
