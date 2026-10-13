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
     * Descuento comercial del proveedor, en % sobre el precio base, para todo el documento (solo facturas; 0 = sin descuento).
     * El `coste` de cada línea ya es el neto (precio de lista menos este %): por eso FIFO, coste del artículo, margen, base e
     * IVA del documento no necesitan saber nada del descuento.
     */
    @ColumnInfo(defaultValue = "0")
    val descuento: Double = 0.0
)

/** Coste neto de una línea: precio base de lista menos el descuento (%) del documento, a 4 decimales. */
fun costeNeto(lista: Double, descuentoPorcentaje: Double): Double =
    if (descuentoPorcentaje <= 0.0) lista else Math.round(lista * (1 - descuentoPorcentaje / 100) * 10000) / 10000.0
