package com.example.magatzem.data

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
    val estadoFacturacion: String = ESTADO_FACTURACION_PENDIENTE
)
