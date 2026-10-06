package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Fila única (id siempre 1) con los datos de la propia empresa, para futuros documentos/informes. */
@Entity(tableName = "datos_empresa")
data class DatosEmpresaEntity(
    @PrimaryKey
    val id: Long = 1,
    val nombre: String = "",
    /** Marca con la que opera de cara al público, si es distinta de la razón social ([nombre]). */
    val nombreComercial: String? = null,
    val nifCif: String? = null,
    val direccion: String? = null,
    val codigoPostal: String? = null,
    val ciudad: String? = null,
    val telefono: String? = null,
    val email: String? = null
)
