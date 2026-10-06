package com.example.magatzem.data

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
    val subtotalConIva: Double
)
