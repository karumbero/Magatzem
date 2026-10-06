package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Albarán de una recepción. Solo la fecha es obligatoria; número, base e IVA son libres. */
@Entity(
    tableName = "albaranes",
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
data class AlbaranEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recepcionId: Long?,
    val numero: String?,
    val fecha: String,
    val base: Double?,
    val iva: Double?,
    val uuid: String = UUID.randomUUID().toString()
)
