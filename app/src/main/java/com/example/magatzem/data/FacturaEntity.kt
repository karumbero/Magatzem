package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Estado de pago de una factura (no confundir con [ESTADO_FACTURACION_PENDIENTE] de la recepción). */
const val ESTADO_FACTURA_PENDIENTE = "pendiente"
const val ESTADO_FACTURA_PAGADA = "pagada"

/** Factura de una recepción: fecha, base e IVA obligatorios (se calculan de las líneas); número libre. */
@Entity(
    tableName = "facturas",
    foreignKeys = [
        ForeignKey(
            entity = RecepcionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recepcionId"],
            onDelete = SET_NULL
        )
    ],
    indices = [Index(value = ["recepcionId"]), Index(value = ["uuid"], unique = true)]
)
data class FacturaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recepcionId: Long?,
    val numero: String?,
    val fecha: String,
    val base: Double,
    val iva: Double,
    val estado: String = ESTADO_FACTURA_PENDIENTE,
    val uuid: String = UUID.randomUUID().toString()
)
