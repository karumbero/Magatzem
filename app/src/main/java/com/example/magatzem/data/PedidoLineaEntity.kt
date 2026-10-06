package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pedido_lineas",
    foreignKeys = [
        ForeignKey(
            entity = PedidoEntity::class,
            parentColumns = ["id"],
            childColumns = ["pedidoId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductoEntity::class,
            parentColumns = ["id"],
            childColumns = ["productoId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["pedidoId"]), Index(value = ["productoId"])]
)
data class PedidoLineaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pedidoId: Long,
    val productoId: Long,
    val cantidad: Int,
    /** Coste unitario en el momento de guardar el pedido (sin IVA ni recargo). */
    val coste: Double,
    /** cantidad * coste, sin impuestos. */
    val subtotal: Double
)
