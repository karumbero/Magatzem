package com.example.magatzem.ui.escaneo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.PagoFacturaEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.data.escaneo.FacturaEscaneada
import com.example.magatzem.data.escaneo.ImportacionEscaneo
import com.example.magatzem.data.factorCoste
import com.example.magatzem.ui.documentos.AvisoRemarcar
import com.example.magatzem.ui.documentos.DocumentoMotor
import com.example.magatzem.ui.documentos.TipoDoc
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EstadoLinea { SIN_COMPROBAR, NO_EXISTE, EXISTE, COSTE_MAS_ALTO }

/** Una línea de la factura de Escaneo con lo que se sabe de ella en Magatzem. */
class LineaImportada(val ref: String, val descripcion: String, val cantidad: Double, val precio: Double?, val dto: Double?) {
    var estado by mutableStateOf(EstadoLinea.SIN_COMPROBAR)
    var producto by mutableStateOf<ProductoEntity?>(null)
    var pvpRecomendado by mutableStateOf<Double?>(null)
    var actualizarPvp by mutableStateOf(true)
    /** Precio base de una unidad tras el descuento de la línea. */
    val costeNeto: Double? get() = precio?.let { it * (1 - (dto ?: 0.0) / 100) }
}

private fun norm(t: String) = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
private fun r2(x: Double) = Math.round(x * 100) / 100.0

/**
 * Importar la factura (o albarán) que manda Escaneo: se comprueba cada línea por proveedor y referencia (el SKU de Magatzem es «ID del proveedor/REF»), se pueden añadir los artículos
 * que faltan, se avisa de los que llegan con coste más alto (PVP anterior y PVP recomendado) y, al darle entrada, se crea con las mismas reglas que una entrada tecleada: sube la
 * existencia, se crea la capa de coste y se actualizan coste, margen y PVP. Los plazos y la forma de pago de la factura se dejan planificados.
 */
class ImportarEscaneoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val motor = DocumentoMotor(db)

    val lineas = mutableStateListOf<LineaImportada>()
    var proveedor by mutableStateOf<ProveedorEntity?>(null)
        private set
    var categorias by mutableStateOf<List<CategoriaEntity>>(emptyList())
        private set
    var mensaje by mutableStateOf<String?>(null)
    /** Quien pueda probar con un proveedor sin configurar (nivel 1); el resto solo ve el aviso y entra la factura a mano. */
    var puedeProbar = false
    /** «Crear los productos que faltan» al dar entrada, todos en esta categoría (con su SKU «ID del proveedor/REF»). */
    var crearFaltantes by mutableStateOf(false)
    var categoriaFaltantes by mutableStateOf<Long?>(null)
    val faltan: Int get() = lineas.count { it.estado == EstadoLinea.NO_EXISTE }
    var ocupado by mutableStateOf(false)
        private set
    var lineaNueva by mutableStateOf<LineaImportada?>(null)
    var avisosRemarcar by mutableStateOf<List<AvisoRemarcar>>(emptyList())
    var resultado by mutableStateOf<String?>(null)
        private set
    /** Aviso fijo cuando el proveedor aún no tiene facturas PDF procesadas con éxito. */
    val avisoProveedor: String?
        get() = proveedor?.takeIf { !it.facturasPdf }?.let {
            if (puedeProbar) "«${it.nombre}» aún no tiene el proceso de facturas PDF configurado. Puedes probar: si sale bien, se marca como configurado; si no, entra la factura a mano y contacta con Toni."
            else "«${it.nombre}» no tiene el proceso de facturas PDF configurado: entra la factura a mano y contacta con Toni para que lo configuremos."
        }
    private var cargadaDe: FacturaEscaneada? = null

    /** Se llama al abrir la pantalla con una factura nueva: prepara las líneas y las comprueba. */
    fun preparar(f: FacturaEscaneada) {
        if (cargadaDe === f) return
        cargadaDe = f
        lineas.clear(); lineas += f.lineas.map { LineaImportada(it.ref, it.descripcion, it.cantidad, it.precio, it.dto) }
        resultado = null; mensaje = null
        comprobar(f)
    }

    fun comprobar(f: FacturaEscaneada) {
        viewModelScope.launch {
            categorias = db.categoriaDao().obtenerTodas().sortedBy { norm(it.nombre) }
            val prov = db.proveedorDao().obtenerTodos().firstOrNull { norm(it.nombre) == norm(f.proveedor) }
            proveedor = prov
            if (prov == null) { mensaje = "El proveedor «${f.proveedor}» no existe en Magatzem: créalo (Proveedores) y vuelve a mandar la factura."; lineas.forEach { it.estado = EstadoLinea.SIN_COMPROBAR }; return@launch }
            val productos = db.productoDao().obtenerTodosOrdenados()
            lineas.forEach { l ->
                val p = productos.firstOrNull { it.sku.equals("${prov.id}/${l.ref}", ignoreCase = true) }
                    ?: productos.firstOrNull { it.proveedorId == prov.id && it.referenciaFabricante.equals(l.ref, ignoreCase = true) }
                    // Artículos antiguos con un SKU que no sigue la regla («selloA», «09/postal») y sin referencia de fabricante: vale lo que va tras la última barra.
                    ?: productos.firstOrNull { it.proveedorId == prov.id && it.sku?.substringAfterLast('/').equals(l.ref, ignoreCase = true) }
                l.producto = p
                val neto = l.costeNeto
                when {
                    p == null -> { l.estado = EstadoLinea.NO_EXISTE; l.pvpRecomendado = null }
                    neto != null && p.coste > 0 && neto > p.coste + 0.005 -> {
                        l.estado = EstadoLinea.COSTE_MAS_ALTO
                        l.pvpRecomendado = if (p.margenBeneficio > 0) r2(ProductoEntity.calcularPrecioVenta(neto, p.margenBeneficio, prov.exentoIva)) else p.precioVenta
                    }
                    else -> { l.estado = EstadoLinea.EXISTE; l.pvpRecomendado = null }
                }
            }
            val faltan = lineas.count { it.estado == EstadoLinea.NO_EXISTE }; val caros = lineas.count { it.estado == EstadoLinea.COSTE_MAS_ALTO }
            mensaje = if (faltan == 0 && caros == 0) "Todas las líneas existen en Magatzem." else listOfNotNull(
                if (faltan > 0) "$faltan sin producto en Magatzem" else null, if (caros > 0) "$caros con coste más alto" else null).joinToString(" · ")
        }
    }

    /** «Añadir producto»: lo da de alta con existencia 0 (las unidades entran con la factura) y vuelve a comprobar. */
    fun anadirProducto(f: FacturaEscaneada, nombre: String, sku: String, ref: String, categoriaId: Long, base: Double, margen: Double, pvp: Double) {
        val prov = proveedor ?: return
        viewModelScope.launch {
            val dup = db.productoDao().buscarDuplicado(sku.trim().ifBlank { null }, "", ref.trim())
            if (dup != null) { mensaje = "Ya existe «${dup.nombre}» con ese SKU o referencia."; return@launch }
            val ahora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            db.productoDao().insert(ProductoEntity(
                sku = sku.trim().ifBlank { null }, referenciaFabricante = ref.trim().ifBlank { null }, nombre = nombre.trim(), categoriaId = categoriaId, proveedorId = prov.id,
                coste = base, margenBeneficio = margen, precioVenta = pvp, existencia = 0, fechaCreacion = ahora, fechaActualizacion = ahora
            ))
            lineaNueva = null
            comprobar(f)
        }
    }

    /** Cierra la pantalla (se descarta lo recibido si no se le dio entrada). */
    fun cerrar() { crearFaltantes = false; categoriaFaltantes = null; ImportacionEscaneo.pendiente = null; cargadaDe = null; lineas.clear(); lineaNueva = null; avisosRemarcar = emptyList(); resultado = null; mensaje = null }

    /** Da entrada: crea la factura (o albarán) con sus líneas y deja planificados sus plazos. */
    fun darEntrada(f: FacturaEscaneada) {
        val prov = proveedor ?: run { mensaje = "El proveedor no existe en Magatzem."; return }
        if (!prov.facturasPdf && !puedeProbar) { mensaje = avisoProveedor; return }
        val sin = lineas.filter { it.estado == EstadoLinea.NO_EXISTE || it.estado == EstadoLinea.SIN_COMPROBAR }
        val faltantes = lineas.filter { it.estado == EstadoLinea.NO_EXISTE }
        if (crearFaltantes && faltantes.isNotEmpty() && categoriaFaltantes == null) { mensaje = "Elige la categoría para los ${faltantes.size} productos que se van a crear."; return }
        if (sin.isNotEmpty() && !(crearFaltantes && sin.size == faltantes.size)) { mensaje = "Faltan productos en Magatzem (pulsa «Añadir producto» en esas líneas o activa «Crear los productos que faltan»): " + sin.joinToString(", ") { it.ref.ifBlank { it.descripcion } }; return }
        if (f.numero.isBlank()) { mensaje = "Falta el número del documento."; return }
        val malas = lineas.filter { it.cantidad <= 0 || it.cantidad != Math.floor(it.cantidad) || (it.costeNeto ?: -1.0) < 0 }
        if (malas.isNotEmpty()) { mensaje = "Revisa cantidad y precio de: " + malas.joinToString(", ") { it.ref.ifBlank { it.descripcion } } + " (la cantidad debe ser un número entero)."; return }
        val factor = factorCoste(prov.exentoIva)
        val suma = lineas.sumOf { r2(it.cantidad * (it.costeNeto ?: 0.0) * factor) } * (1 - f.descuentoPct / 100)
        f.total?.let { if (Math.abs(suma - it) > 0.05) { mensaje = "Las líneas suman %.2f € con IVA y recargo y el total del documento es %.2f €. Revisa las líneas en Escaneo.".format(suma, it).replace('.', ','); return } }
        ocupado = true; mensaje = null
        viewModelScope.launch {
            try {
                val tipo = if (f.esAlbaran) TipoDoc.ALBARAN else TipoDoc.FACTURA
                if (motor.buscarPorNumero(tipo, prov.id, f.numero) != null) { mensaje = "Ya existe ${if (f.esAlbaran) "el albarán" else "la factura"} ${f.numero} de ese proveedor: no se vuelve a crear."; return@launch }
                // Productos que faltan: se crean con su SKU, nombre y precio de la factura (existencia 0: las unidades entran con la factura). El margen queda en 0 %: se ajusta después.
                val creados = mutableListOf<String>()
                if (crearFaltantes) for (l in faltantes) {
                    val sku = "${prov.id}/${l.ref}"
                    val ya = db.productoDao().buscarDuplicado(sku, "", l.ref)
                    val p = ya ?: run {
                        val ahora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        val base = l.costeNeto ?: 0.0
                        val nuevo = ProductoEntity(sku = sku, referenciaFabricante = l.ref.ifBlank { null }, nombre = l.descripcion.ifBlank { l.ref }, categoriaId = categoriaFaltantes, proveedorId = prov.id,
                            coste = base, margenBeneficio = 0.0, precioVenta = r2(ProductoEntity.calcularPrecioVenta(base, 0.0, prov.exentoIva)), existencia = 0, fechaCreacion = ahora, fechaActualizacion = ahora)
                        creados += l.ref
                        nuevo.copy(id = db.productoDao().insert(nuevo))
                    }
                    l.producto = p; l.estado = EstadoLinea.EXISTE
                }
                val docId = motor.crear(tipo, prov.id, f.numero, f.fechaIso)
                val doc = motor.abrir(tipo, docId) ?: error("No se pudo abrir el documento creado")
                val avisos = mutableListOf<AvisoRemarcar>(); val errores = mutableListOf<String>()
                for (l in lineas) {
                    val p = l.producto ?: continue
                    val pvpAntes = p.precioVenta; val margenAntes = p.margenBeneficio
                    val coste = l.costeNeto ?: 0.0
                    val r = motor.guardarLinea(doc.recepcionId, p.id, l.cantidad.toInt(), coste)
                    r.error?.let { errores += "${l.ref}: $it" }
                    // «Actualizar PVP» desactivado: el coste nuevo entra, pero el PVP se queda como estaba (el margen se recalcula con él).
                    if (l.estado == EstadoLinea.COSTE_MAS_ALTO && !l.actualizarPvp && pvpAntes > 0 && r.error == null) {
                        val nuevo = db.productoDao().obtenerPorId(p.id)
                        if (nuevo != null) db.productoDao().update(nuevo.copy(precioVenta = pvpAntes, margenBeneficio = if (coste > 0) r2((pvpAntes / (coste * factor) - 1) * 100) else margenAntes))
                    } else r.remarcar?.let { avisos += it }
                }
                if (!f.esAlbaran && f.plazos.isNotEmpty()) {
                    val formas = db.formaPagoDao().obtenerTodas()
                    val forma = formas.firstOrNull { norm(it.nombre) == norm(f.formaPago) } ?: formas.firstOrNull { norm(it.nombre).contains(norm(f.formaPago)) && f.formaPago.isNotBlank() }
                    db.withTransaction {
                        db.pagoFacturaDao().insertar(f.plazos.mapIndexed { i, p ->
                            PagoFacturaEntity(facturaId = docId, formaPagoId = forma?.id, numeroPlazo = i + 1, totalPlazos = f.plazos.size,
                                importe = p.importe ?: r2((f.total ?: suma) / f.plazos.size), fechaPrevista = p.fechaIso)
                        })
                    }
                }
                if (f.descuentoPct > 0) motor.cambiarDescuento(doc.recepcionId, f.descuentoPct)   // aparte: no cambia el coste ni el PVP de los artículos
                avisosRemarcar = avisos
                if (errores.isEmpty() && !prov.facturasPdf) {
                    db.proveedorDao().marcarFacturasPdf(prov.id)
                    proveedor = prov.copy(facturasPdf = true)
                }
                resultado = (if (f.esAlbaran) "Albarán " else "Factura ") + f.numero + " creada en Magatzem con ${lineas.size} líneas." +
                    (if (errores.isNotEmpty()) " Algunas líneas dieron error: " + errores.joinToString("; ") else "") +
                    (if (!f.esAlbaran && f.plazos.isNotEmpty()) " Vencimientos planificados: ${f.plazos.size}." else "") +
                    (if (creados.isNotEmpty()) " Productos nuevos creados: ${creados.size} (margen 0 %: revisa su PVP)." else "")
            } catch (e: Exception) {
                mensaje = "No se pudo dar entrada: ${e.message ?: e.javaClass.simpleName}"
            } finally { ocupado = false }
        }
    }
}
