package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro de cada exportación al programa de ventas: solo sirve para saber si ya se exportó alguna
 * vez y, si es así, desde cuándo hay que mandar artículos nuevos/modificados. La primera vez
 * (tabla vacía) se manda todo: usuarios, datos de empresa, proveedores, categorías y artículos. Las
 * siguientes, solo los artículos con `fechaActualizacion` posterior a la última exportación — nunca
 * existencias (ver `magatzem-project` en memoria: el programa de ventas no las necesita).
 */
@Entity(tableName = "exportaciones_catalogo")
data class ExportacionCatalogoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fecha: String,
    val dispositivoId: String,
    val cantidadArticulos: Int,
    val nombreArchivo: String,
    /** Huella de categorías, proveedores y artículos existentes en esa exportación (ver [HuellaCatalogo]); null en las anteriores a esto. */
    val huellaMaestros: String? = null
)
