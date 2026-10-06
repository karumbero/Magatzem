package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Libro de la caja de la tienda (el efectivo). Cada cierre de MiTPV importado añade: el saldo con el que
 * empezó (solo si no cuadra con lo que ya había), las ventas al contado, cada ingreso a banco (sale
 * de caja). El descuadre del arqueo no entra (MiTPV arrastra el efectivo esperado). El saldo de caja es la suma de los importes.
 */
@Entity(
    tableName = "movimientos_caja",
    indices = [Index(value = ["cierreId"]), Index(value = ["uuid"], unique = true)]
)
data class MovimientoCajaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    /** yyyy-MM-dd HH:mm:ss */
    val fecha: String,
    val concepto: String,
    /** Positivo entra en caja, negativo sale. */
    val importe: Double,
    /** Cierre de MiTPV que lo originó (null = movimiento manual). */
    val cierreId: Long? = null
)

/**
 * Libro de un banco (una forma de pago que es cuenta bancaria): cobros con tarjeta de los cierres y
 * los ingresos de efectivo desde caja. El saldo de cada banco es la suma de sus importes.
 */
@Entity(
    tableName = "movimientos_banco",
    foreignKeys = [
        ForeignKey(entity = FormaPagoEntity::class, parentColumns = ["id"], childColumns = ["bancoId"], onDelete = SET_NULL),
        ForeignKey(entity = PagoFacturaEntity::class, parentColumns = ["id"], childColumns = ["pagoId"], onDelete = SET_NULL)
    ],
    indices = [Index(value = ["bancoId"]), Index(value = ["cierreId"]), Index(value = ["pagoId"]), Index(value = ["uuid"], unique = true)]
)
data class MovimientoBancoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val bancoId: Long?,
    val fecha: String,
    val concepto: String,
    /** Positivo ingresa en el banco, negativo sale. */
    val importe: Double,
    val cierreId: Long? = null,
    /** Pago de factura que originó este movimiento (sale del banco); null si viene de un cierre. */
    val pagoId: Long? = null
)
