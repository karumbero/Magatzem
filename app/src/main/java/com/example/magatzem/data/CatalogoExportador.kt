package com.example.magatzem.data

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporta el catálogo al programa de ventas (ver `CatalogoMagatzemJson`). El usuario lo pide a mano
 * desde Ajustes → Exportar: cada exportación es un envío distinto (un fichero por exportación, no se
 * sobrescribe el anterior) que luego hay que llevar físicamente al otro aparato.
 *
 * La **primera** exportación es la apertura: además de mandar todo (ver `CatalogoMagatzemJson`),
 * **borra** el histórico de Entradas (recepciones, líneas, albaranes y facturas), que solo sirvió
 * para preparar el stock antes de abrir. No toca la existencia de los artículos. Todo ocurre en
 * una sola transacción: si algo falla, no se registra la exportación ni se borra nada.
 */
class CatalogoExportador(
    private val context: Context,
    private val db: AppDatabase,
    private val dispositivoId: String
) {
    /** Carpeta de exportación; si no hay almacenamiento externo, la interna de la app. */
    fun carpeta(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "exportacion").also { it.mkdirs() }

    /**
     * Escribe el fichero (si hay algo que mandar) y registra la exportación. Devuelve el fichero
     * escrito, o null si no era la primera vez y no había nada nuevo: ni artículos modificados ni cambios o borrados.
     */
    suspend fun exportar(): File? {
        val ahora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val nombreArchivo = "magatzem_" +
            SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date()) + ".json"

        return db.withTransaction {
            val ultima = db.exportacionCatalogoDao().obtenerUltima()
            val esPrimera = ultima == null

            val articulos = if (esPrimera) {
                db.productoDao().obtenerTodosOrdenados()
            } else {
                db.productoDao().pendientesDesde(ultima!!.fecha)
            }
            val categorias = db.categoriaDao().obtenerTodas()
            val proveedores = db.proveedorDao().obtenerTodos()
            val uuidsArticulos = db.productoDao().obtenerUuids()
            val usuariosNivel1 = db.usuarioDao().obtenerTodos().filter { it.nivel == 1 }
            val huella = HuellaCatalogo.calcular(categorias, proveedores, uuidsArticulos, usuariosNivel1)
            // Hay algo que exportar si hay artículos nuevos/modificados O ha cambiado (o se ha borrado)
            // algo de categorías, proveedores o artículos desde la última exportación.
            val cambiaronMaestros = esPrimera || ultima!!.huellaMaestros != huella
            if (!esPrimera && articulos.isEmpty() && !cambiaronMaestros) return@withTransaction null
            val categoriaUuidPorId = categorias.associate { it.id to it.uuid }
            val proveedorUuidPorId = proveedores.associate { it.id to it.uuid }

            val json = CatalogoMagatzemJson.construir(
                primeraExportacion = esPrimera,
                // Solo los usuarios de nivel 1, y en TODAS las exportaciones: MiTPV tiene sus propios usuarios (cajeros) y de Magatzem solo recibe estos.
                usuarios = usuariosNivel1,
                datosEmpresa = if (esPrimera) db.datosEmpresaDao().obtener() else null,
                formasPago = if (esPrimera) db.formaPagoDao().obtenerTodas() else null,
                // Proveedores y categorías van en TODAS las exportaciones (el programa de ventas los crea
                // o actualiza por uuid, sin borrar): así un artículo nuevo no llega con una categoría o un
                // proveedor que allí todavía no existe. Usuarios, empresa y formas de pago, solo la primera vez.
                proveedores = proveedores,
                categorias = categorias,
                articulos = articulos,
                articulosVigentes = uuidsArticulos,
                categoriaUuidPorId = categoriaUuidPorId,
                proveedorUuidPorId = proveedorUuidPorId,
                origen = dispositivoId,
                exportadoEn = ahora,
                // La caja inicial viaja solo en la apertura (primera exportación): MiTPV la usa como valor fijo al abrir caja.
                cajaInicial = if (esPrimera) db.ajusteDao().valor(AJUSTE_CAJA_INICIAL)?.toDoubleOrNull()?.takeIf { it > 0.0 } else null
            )
            val destino = File(carpeta(), nombreArchivo)
            destino.writeText(json, Charsets.UTF_8)

            db.exportacionCatalogoDao().insertar(
                ExportacionCatalogoEntity(
                    fecha = ahora,
                    dispositivoId = dispositivoId,
                    cantidadArticulos = articulos.size,
                    nombreArchivo = nombreArchivo,
                    huellaMaestros = huella
                )
            )
            if (esPrimera) {
                db.recepcionDao().borrarAlbaranes()
                db.recepcionDao().borrarFacturas()
                db.recepcionDao().borrarRecepciones()
                crearCapaInicial(ahora)
            }
            destino
        }
    }

    /**
     * Tras borrar el histórico de entradas, la existencia de cada artículo pasa a ser una **capa inicial** a su coste actual: todo
     * unificado, sin costes distintos por lotes (ver `CapasFifo`). Es una entrada interna "Existencia inicial", sin proveedor, albarán ni
     * factura. Los artículos sin existencia no tienen capa; uno con existencia y coste 0 tiene una capa sin coste, que se valora al
     * coste actual del artículo cuando se le ponga uno.
     */
    private suspend fun crearCapaInicial(ahora: String) {
        val exentos = db.proveedorDao().obtenerTodos().filter { it.exentoIva }.map { it.id }.toSet()
        val conExistencia = db.productoDao().obtenerTodosOrdenados().filter { it.existencia > 0 }
        if (conExistencia.isEmpty()) return
        val lineas = conExistencia.map { p ->
            RecepcionLineaEntity(
                recepcionId = 0, productoId = p.id, cantidad = p.existencia, coste = p.coste, margenBeneficio = p.margenBeneficio,
                subtotalConIva = p.existencia * p.coste * factorCoste(p.proveedorId in exentos)
            )
        }
        val recepcionId = db.recepcionDao().insertarRecepcion(
            RecepcionEntity(proveedorId = null, fecha = ahora, totalConIva = lineas.sumOf { it.subtotalConIva })
        )
        db.recepcionDao().insertarLineas(lineas.map { it.copy(recepcionId = recepcionId) })
    }
}
