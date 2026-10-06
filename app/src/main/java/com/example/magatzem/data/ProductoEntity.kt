package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Distinción "Pre" (ya existía físicamente, coste a veces desconocido) vs "Nuevo" (alta con
 * proveedor/factura real) **descartada el 2026-09-30**: todo se da de alta y se edita por igual
 * desde "Existencia", sin filtrar por esto en ningún sitio. La columna se deja en el esquema (para
 * no forzar una migración de tabla sin necesidad, con datos reales ya en el Lenovo) pero ya no
 * tiene ningún efecto en la app.
 */
const val TIPO_PRODUCTO_NUEVO = "NUEVO"
const val TIPO_PRODUCTO_PRE = "PRE"

/** IVA fijo del 21% (multiplicador: coste * IVA = coste con IVA incluido), no varía por artículo de momento. */
const val IVA = 1.21

/**
 * Recargo de equivalencia: 5,2% adicional sobre el precio base que el proveedor cobra al comprar
 * mercancía (régimen especial de recargo de equivalencia). Igual que en BackShop.
 */
const val RECARGO_EQUIVALENCIA = 0.052

/** Multiplicador para pasar de precio base a "precio coste" (precio base con IVA y recargo incluidos). */
const val IVA_MAS_RECARGO = IVA + RECARGO_EQUIVALENCIA

/** Multiplicadores según el proveedor: uno exento de IVA (sellos de Correos) no lleva IVA ni recargo. */
fun factorIva(exento: Boolean): Double = if (exento) 1.0 else IVA
fun factorRecargo(exento: Boolean): Double = if (exento) 0.0 else RECARGO_EQUIVALENCIA
fun factorCoste(exento: Boolean): Double = if (exento) 1.0 else IVA_MAS_RECARGO

@Entity(
    tableName = "productos",
    foreignKeys = [
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = SET_NULL
        ),
        ForeignKey(
            entity = ProveedorEntity::class,
            parentColumns = ["id"],
            childColumns = ["proveedorId"],
            onDelete = SET_NULL
        )
    ],
    indices = [
        Index(value = ["sku"], unique = true),
        Index(value = ["codigoBarras"], unique = true),
        Index(value = ["uuid"], unique = true),
        Index(value = ["categoriaId"]),
        Index(value = ["proveedorId"])
    ]
)
data class ProductoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Identificación: nada de esto es obligatorio (ver AppDatabase.MIGRATION_1_2), para poder
    // entrar artículos deprisa y completarlos más adelante.
    val sku: String? = null,
    val codigoBarras: String? = null,
    val referenciaFabricante: String? = null,
    val nombre: String,
    val categoriaId: Long? = null,
    val proveedorId: Long? = null,
    /** Sin uso real (ver comentario junto a [TIPO_PRODUCTO_NUEVO]); se conserva solo por el esquema. */
    val tipo: String = TIPO_PRODUCTO_NUEVO,
    // Precios
    val coste: Double = 0.0,
    val margenBeneficio: Double = 0.0,
    val precioVenta: Double = 0.0,
    // Stock: un único número, de momento (sin capas de coste ni reparto almacén/tienda todavía).
    val existencia: Int = 0,
    val minimo: Int = 0,
    val maximo: Int? = null,
    /** Si el artículo sale en las teclas programables de la pantalla de Ventas de MiTPV (los de código de barras, no). */
    val mostrarEnTeclado: Boolean = true,
    // Auditoría
    val fechaCreacion: String,
    val fechaActualizacion: String,
    /** Identidad estable entre aparatos (los `id` autonuméricos son locales). */
    val uuid: String = UUID.randomUUID().toString()
) {
    companion object {
        /**
         * Precio coste = precio base + IVA (21% del base) + recargo de equivalencia (5,2% del
         * base) = coste * [IVA_MAS_RECARGO]. PVP = precio coste + margen (el margen es un
         * porcentaje sobre el precio COSTE, no sobre el precio base). Con coste 0 (Pre-Stock sin
         * coste todavía conocido), esto da PVP = 0 sin más: el PVP real se teclea directamente en
         * el formulario mientras tanto, sin que dependa de esta fórmula.
         */
        fun calcularPrecioVenta(coste: Double, margenBeneficio: Double, exentoIva: Boolean = false): Double =
            coste * factorCoste(exentoIva) * (1 + margenBeneficio / 100)
    }
}
