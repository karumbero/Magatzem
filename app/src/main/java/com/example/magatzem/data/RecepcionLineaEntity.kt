package com.example.magatzem.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recepcion_lineas",
    foreignKeys = [
        ForeignKey(
            entity = RecepcionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recepcionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductoEntity::class,
            parentColumns = ["id"],
            childColumns = ["productoId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["recepcionId"]), Index(value = ["productoId"])]
)
data class RecepcionLineaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recepcionId: Long,
    val productoId: Long,
    val cantidad: Int,
    val coste: Double,
    val margenBeneficio: Double,
    val subtotalConIva: Double,
    /**
     * Precio base de lista tal como lo da el proveedor, antes del descuento del documento (ver [RecepcionEntity.descuento]);
     * `coste` es el neto. 0 = sin dato (líneas anteriores al descuento): vale `coste`.
     */
    @ColumnInfo(defaultValue = "0")
    val costeLista: Double = 0.0
) {
    /** Precio de lista de la línea: lo que se teclea y se ve; si no hay dato, el propio coste. */
    val precioLista: Double get() = if (costeLista > 0.0) costeLista else coste
}
