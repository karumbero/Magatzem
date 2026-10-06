package com.example.magatzem.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Estas dos formas de pago siempre existen (sembradas al crear la base de datos) y no son una
 * cuenta bancaria: no tiene sentido pedirles IBAN/titular. Cualquier otra fila del catálogo (Banca
 * March hoy, o las que se añadan) se trata como un banco.
 */
const val NOMBRE_CONTADO = "Contado"
const val NOMBRE_TRANSFERENCIA = "Transferencia"

/** Catálogo de formas de pago (Contado, bancos, transferencia…), editable desde Ajustes. */
@Entity(
    tableName = "formas_pago",
    indices = [Index(value = ["nombre"], unique = true), Index(value = ["uuid"], unique = true)]
)
data class FormaPagoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombre: String,
    /** Una inactiva no se ofrece para pagar cosas nuevas, pero lo que ya se creó con ella se conserva. */
    val activo: Boolean = true,
    /** Solo tienen sentido cuando esta fila es un banco (ver [NOMBRE_CONTADO]/[NOMBRE_TRANSFERENCIA]). */
    val iban: String? = null,
    val titular: String? = null,
    /**
     * El banco que recibe los cobros con tarjeta y los ingresos de caja al importar un cierre de MiTPV.
     * Con un solo banco no hace falta marcarlo; con varios, hay que elegir uno (Ajustes → Bancos).
     */
    @ColumnInfo(defaultValue = "0")
    val principal: Boolean = false,
    /** Identidad estable entre aparatos (los `id` autonuméricos son locales). */
    val uuid: String = UUID.randomUUID().toString()
)

/** Cualquier forma de pago que no sea Contado ni Transferencia se considera una cuenta bancaria. */
fun FormaPagoEntity.esBanco(): Boolean = nombre != NOMBRE_CONTADO && nombre != NOMBRE_TRANSFERENCIA
