package com.example.magatzem.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey

const val ESTADO_FACTURACION_PENDIENTE = "pendiente"

/**
 * Una entrada de mercancía de un proveedor (Movimientos → Entradas), con su albarán, factura o
 * Pre-Stock asociado. Igual que en BackShop, pero sin destino almacén/tienda ni capas de coste: aquí
 * `existencia` es un único número (ver [ProductoEntity]).
 */
@Entity(
    tableName = "recepciones",
    foreignKeys = [
        ForeignKey(
            entity = ProveedorEntity::class,
            parentColumns = ["id"],
            childColumns = ["proveedorId"],
            onDelete = SET_NULL
        )
    ],
    indices = [Index(value = ["proveedorId"])]
)
data class RecepcionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val proveedorId: Long?,
    val fecha: String,
    val totalConIva: Double,
    val estadoFacturacion: String = ESTADO_FACTURACION_PENDIENTE,
    /**
     * Descuento comercial del proveedor, en % sobre el precio base de las líneas, para todo el documento (albarán o factura; 0 = sin descuento).
     * NO cambia el coste ni el PVP de los artículos (el `coste` de cada línea es el precio de lista): solo baja lo que vale el documento, y el importe
     * descontado se anota aparte como beneficio del proveedor (ver [DescuentoComercialEntity]).
     */
    @ColumnInfo(defaultValue = "0")
    val descuento: Double = 0.0,
    /** Gastos de envío del documento (base, sin IVA): llevan el mismo IVA y recargo que el resto, no se descuentan y no cambian el coste de los artículos. */
    @ColumnInfo(defaultValue = "0")
    val gastosEnvio: Double = 0.0
)
