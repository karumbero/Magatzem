package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Un pago (o plazo) de una factura. Contado y transferencia son un único pago con su fecha, ya
 * pagado; un cargo por banco son 1, 2 o 3 plazos, cada uno con su importe y fecha prevista, que se
 * van dando por pagados con su fecha. La factura está pagada cuando todos sus pagos lo están.
 */
@Entity(
    tableName = "pagos_factura",
    foreignKeys = [
        ForeignKey(entity = FacturaEntity::class, parentColumns = ["id"], childColumns = ["facturaId"], onDelete = CASCADE),
        ForeignKey(entity = FormaPagoEntity::class, parentColumns = ["id"], childColumns = ["formaPagoId"], onDelete = SET_NULL)
    ],
    indices = [Index(value = ["facturaId"]), Index(value = ["formaPagoId"]), Index(value = ["uuid"], unique = true)]
)
data class PagoFacturaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val facturaId: Long,
    val formaPagoId: Long?,
    /** 1..[totalPlazos]. */
    val numeroPlazo: Int,
    val totalPlazos: Int,
    val importe: Double,
    /** yyyy-MM-dd: cuándo está previsto cargarlo. */
    val fechaPrevista: String,
    /** yyyy-MM-dd: cuándo se pagó; null = pendiente. */
    val fechaPago: String? = null
)
