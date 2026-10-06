package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un cierre de caja importado desde MiTPV (fichero `mitpv-cierre`). `uuid` es el del cierre en MiTPV y
 * es único aquí: así un mismo cierre no se puede importar dos veces. Los totales son los del cierre
 * ya ajustados con el datáfono (los que valen para el dinero por forma de pago).
 */
@Entity(tableName = "cierres_importados", indices = [Index(value = ["uuid"], unique = true)])
data class CierreImportadoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val origen: String,
    val abiertoEn: String?,
    val cerradoEn: String,
    val cajeroNombre: String,
    val saldoInicial: Double,
    val ventaContadoTickets: Double,
    val ventaTarjetaTickets: Double,
    val totalDatafono: Double,
    val ajusteTarjeta: Double,
    val ventaContado: Double,
    val ventaTarjeta: Double,
    val retiradoBanco: Double,
    val efectivoEsperado: Double,
    val efectivoContado: Double,
    val diferencia: Double,
    val numTickets: Int,
    val primerTicket: Int?,
    val ultimoTicket: Int?,
    val nombreArchivo: String?,
    val importadoEn: String
)

/** Retirada de caja a banco de un cierre importado (para Oficina/Bancos más adelante). */
@Entity(
    tableName = "retiradas_importadas",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["cierreId"])]
)
data class RetiradaImportadaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val cierreId: Long,
    val fecha: String,
    val importe: Double,
    /** "BANCO" (traspaso a banco) o "PROVEEDOR" (pago al contado a un proveedor). */
    @androidx.room.ColumnInfo(defaultValue = "BANCO")
    val tipo: String = "BANCO",
    /** Para un pago a proveedor: a quién/qué se pagó, si MiTPV lo anotó. */
    val nota: String? = null,
    /** Pago a proveedor: proveedor pagado (uuid) y factura que se pagó, tal como los tecleó el cajero en MiTPV. */
    val proveedorUuid: String? = null,
    val numeroFactura: String? = null,
    /** Pago de factura creado a partir de esta salida de caja; null = todavía no se ha encontrado la factura. */
    val pagoFacturaId: Long? = null
)

/** Ticket de un cierre importado. `ventaOriginalUuid` no es null en una devolución (cantidades e importes en negativo). */
@Entity(
    tableName = "ventas_importadas",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["cierreId"])]
)
data class VentaImportadaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val cierreId: Long,
    val numero: Int,
    val fecha: String,
    /** "CONTADO" o "TARJETA" tal como se registró el ticket en MiTPV (el dinero por forma de pago lo da el cierre). */
    val formaPago: String,
    val importeIva: Double,
    val importeTotal: Double,
    val ventaOriginalUuid: String?,
    /** Quién cobró el ticket (uuid del usuario, el mismo que en Ajustes → Usuarios); para informes por cajero. */
    val cajeroUuid: String? = null
)

/**
 * Línea de un ticket importado: producto (por uuid, `productoId` null si aquí no existe → pendiente) y
 * **precio realmente cobrado**. `costeUnitario` es el precio coste del artículo (base + IVA + recargo de
 * equivalencia) en el momento de importar; beneficio = subtotalConIva − cantidad × costeUnitario
 * ("capa 0": la base del beneficio; el FIFO real se hará más adelante en Magatzem), null si el producto
 * no existe.
 */
@Entity(
    tableName = "venta_lineas_importadas",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["ventaId"]), Index(value = ["productoId"])]
)
data class VentaLineaImportadaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val ventaId: Long,
    val productoId: Long?,
    val productoUuid: String?,
    val sku: String?,
    val codigoBarras: String?,
    val descripcion: String,
    val cantidad: Int,
    val precioUnitarioConIva: Double,
    val ivaPorcentaje: Double,
    val subtotalConIva: Double,
    val costeUnitario: Double?,
    /**
     * Proveedor y categoría del artículo en el momento de importar la venta (uuid): así los informes por
     * proveedor o categoría no cambian si luego se recategoriza o se borra el artículo.
     */
    val proveedorUuid: String? = null,
    val categoriaUuid: String? = null
)
