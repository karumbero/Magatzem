package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

const val TIPO_INCIDENCIA_ROBO = "robo"
const val TIPO_INCIDENCIA_ROTURA = "rotura"
const val TIPO_INCIDENCIA_FALTA_ENTRADA = "falta_entrada"

fun nombreTipoIncidencia(tipo: String): String = when (tipo) {
    TIPO_INCIDENCIA_ROBO -> "Robo"
    TIPO_INCIDENCIA_ROTURA -> "Rotura"
    TIPO_INCIDENCIA_FALTA_ENTRADA -> "Falta en entrada"
    else -> tipo
}

/**
 * Artículos dañados o perdidos (Movimientos → Incidencias). Registrar una incidencia resta
 * [cantidad] de la existencia del artículo; recuperarla la devuelve y borra la incidencia.
 * Nombre, SKU y coste del artículo se copian al crearla, para que el histórico siga valiendo si
 * luego cambia o se borra el artículo.
 */
@Entity(
    tableName = "incidencias",
    foreignKeys = [
        ForeignKey(
            entity = ProductoEntity::class,
            parentColumns = ["id"],
            childColumns = ["productoId"],
            onDelete = SET_NULL
        )
    ],
    indices = [Index(value = ["productoId"]), Index(value = ["uuid"], unique = true)]
)
data class IncidenciaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val productoId: Long?,
    val productoNombre: String,
    val productoSku: String?,
    val tipo: String,
    val cantidad: Int,
    /** Coste unitario del artículo cuando se registró: para valorar la pérdida. */
    val costeUnitario: Double,
    /** Marca de tiempo yyyy-MM-dd HH:mm:ss, como pedidos y entradas. */
    val fecha: String,
    val nota: String? = null
)
