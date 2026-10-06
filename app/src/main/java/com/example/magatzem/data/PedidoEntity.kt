package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

const val ESTADO_PEDIDO_PENDIENTE = "pendiente"

/**
 * Pedido a un proveedor (lo que se le va a encargar). Los cuatro importes se congelan al guardar
 * (no se recalculan si más adelante cambia el coste del artículo), igual que en BackShop.
 */
@Entity(
    tableName = "pedidos",
    foreignKeys = [
        ForeignKey(
            entity = ProveedorEntity::class,
            parentColumns = ["id"],
            childColumns = ["proveedorId"],
            onDelete = SET_NULL
        )
    ],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["proveedorId"])]
)
data class PedidoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val proveedorId: Long?,
    val fecha: String,
    val estado: String = ESTADO_PEDIDO_PENDIENTE,
    /** Suma de coste * cantidad de las líneas, sin impuestos. */
    val importe: Double,
    val importeRecargo: Double,
    val importeIva: Double,
    val total: Double
)
