package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "proveedores", indices = [Index(value = ["uuid"], unique = true)])
data class ProveedorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombre: String,
    val comercial: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    val nifCif: String? = null,
    val direccion: String? = null,
    val codigoPostal: String? = null,
    val ciudad: String? = null,
    /** Proveedor sin IVA ni recargo (p. ej. Correos, sellos): su coste y su PVP son la base, sin impuestos. */
    val exentoIva: Boolean = false,
    /** Identidad estable entre aparatos (los `id` autonuméricos son locales). */
    val uuid: String = UUID.randomUUID().toString()
)
