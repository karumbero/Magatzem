package com.example.magatzem.ui.entradas

import com.example.magatzem.ui.common.fechaIso
import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.AlbaranEntity
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.ESTADO_PEDIDO_PENDIENTE
import com.example.magatzem.data.FacturaEntity
import com.example.magatzem.data.PagosMitpv
import com.example.magatzem.data.factorCoste
import com.example.magatzem.data.factorIva
import com.example.magatzem.data.PedidoEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.data.RecepcionEntity
import com.example.magatzem.data.RecepcionLineaEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LineaEntrada(
    val producto: ProductoEntity,
    val coste: String,
    val margen: String,
    val cantidad: String,
    /** Solo en un documento recuperado: lo que esa línea ya tenía entrado (0 si la línea es nueva). */
    val cantidadOriginal: Int = 0,
    val costeOriginal: Double = 0.0
)

/** Albarán o Factura: lo que respalda esta entrada (Pre-Stock de BackShop no se porta a Magatzem). */
enum class TipoDocumento { ALBARAN, FACTURA }

data class DatosDocumento(
    val tipo: TipoDocumento,
    val numero: String,
    val fecha: String,
    val base: String,
    val iva: String
)

private fun nowTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

/**
 * Entradas de mercancía de un proveedor (Movimientos → Entradas), mismo flujo que en BackShop:
 * elegir proveedor (si tiene pedidos pendientes se ofrece abrirlos), escanear/teclear artículos
 * (si no existe, alta rápida), ajustar cantidad y precio base, elegir Albarán/Factura y
 * guardar. Diferencias con BackShop, a propósito: sin destino almacén/tienda, sin movimientos de
 * stock y sin capas de coste (ver magatzem-project: de momento `existencia` es un solo número y el
 * coste es la "capa 0"). Al guardar, el coste/margen/PVP del artículo pasan a ser los de la línea y
 * la cantidad se suma a su existencia.
 */
class EntradaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val productoDao = db.productoDao()
    private val recepcionDao = db.recepcionDao()
    private val pedidoDao = db.pedidoDao()

    val proveedores: StateFlow<List<ProveedorEntity>> = db.proveedorDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorias: StateFlow<List<CategoriaEntity>> = db.categoriaDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var proveedorSeleccionadoId by mutableStateOf<Long?>(null)
        private set

    val lineas = mutableStateListOf<LineaEntrada>()

    var documento by mutableStateOf<DatosDocumento?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Todos los pedidos pendientes (de cualquier proveedor): lo primero que se ve en Entradas. */
    val pedidosPendientes: StateFlow<List<PedidoEntity>> = pedidoDao.observeAll()
        .map { lista -> lista.filter { it.estado == ESTADO_PEDIDO_PENDIENTE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** false = se ve la lista de pedidos pendientes; true = el formulario de la entrada (de un pedido o libre). */
    var enFormulario by mutableStateOf(false)
        private set

    /** Entrada sin pedido: formulario vacío. */
    fun nuevaEntrada() {
        enFormulario = true
    }

    /** Si esta entrada viene de un pedido guardado, su id: se borra al confirmar (ya está atendido). */
    private var pedidoOrigenId: Long? = null

    fun limpiarError() {
        error = null
    }

    /** Suma de cantidad × coste de las líneas actuales: base imponible sugerida del documento. */
    fun calcularBaseActual(): Double =
        lineas.sumOf { (it.cantidad.toIntOrNull() ?: 0) * (it.coste.replace(',', '.').toDoubleOrNull() ?: 0.0) }

    /** Un albarán/factura que ya existía con este número y proveedor, con las líneas que ya tenía entradas. */
    class DocumentoRecuperado(val tipo: TipoDocumento, val numero: String, val fecha: String, val lineas: List<Pair<ProductoEntity, RecepcionLineaEntity>>) {
        val total: Double get() = lineas.sumOf { it.second.subtotalConIva }
    }

    /** Documento recuperado y abierto: sus líneas están en [lineas] (editables) y al guardar se aplican los cambios. */
    var documentoRecuperado by mutableStateOf<DocumentoRecuperado?>(null)
        private set

    /** Vista previa en el diálogo del número: ya existe un documento así (todavía sin abrir). */
    var vistaPrevia by mutableStateOf<DocumentoRecuperado?>(null)
        private set

    private suspend fun buscarDocumento(tipo: TipoDocumento, numero: String): DocumentoRecuperado? {
        val proveedorId = proveedorSeleccionadoId ?: return null
        val limpio = numero.trim()
        if (limpio.isEmpty()) return null
        // Una factura con pagos registrados no se toca (se guarda aparte).
        val (recepcionId, fecha) = when (tipo) {
            TipoDocumento.ALBARAN -> recepcionDao.buscarAlbaran(proveedorId, limpio)?.let { it.recepcionId to it.fecha }
            TipoDocumento.FACTURA -> recepcionDao.buscarFactura(proveedorId, limpio)?.let { f ->
                if (db.pagoFacturaDao().obtenerPorFactura(f.id).isNotEmpty()) null else f.recepcionId to f.fecha
            }
        } ?: return null
        val id = recepcionId ?: return null
        val existentes = recepcionDao.obtenerLineas(id).mapNotNull { l -> productoDao.obtenerPorId(l.productoId)?.let { it to l } }
        return DocumentoRecuperado(tipo, limpio, fecha, existentes)
    }

    /** Mientras se teclea el número en el diálogo: avisa ya si existe. */
    fun comprobarNumero(tipo: TipoDocumento, numero: String) {
        viewModelScope.launch { vistaPrevia = buscarDocumento(tipo, numero) }
    }

    fun descartarVistaPrevia() {
        vistaPrevia = null
    }

    fun guardarDocumento(datos: DatosDocumento) {
        val abierto = documentoRecuperado
        documento = datos
        vistaPrevia = null
        // Mismo documento ya abierto: se sigue en él tal cual (conserva lo editado).
        if (abierto != null && abierto.tipo == datos.tipo && abierto.numero == datos.numero.trim()) return
        cerrarRecuperado()
        abrirRecuperado(datos)
    }

    /**
     * Si el proveedor ya tiene un documento de este tipo con este número, se abre en la propia entrada: sus líneas pasan a
     * la lista (con su cantidad y coste, editables) y al guardar se aplican los cambios a los artículos, como al
     * editarlo desde Oficina. Lo que se escanee a partir de ahí se suma a él.
     */
    private fun abrirRecuperado(datos: DatosDocumento) {
        viewModelScope.launch {
            val rec = buscarDocumento(datos.tipo, datos.numero) ?: return@launch
            // Una misma línea por artículo (un documento fusionado puede repetirlo: se suman).
            val porProducto = linkedMapOf<Long, Pair<ProductoEntity, RecepcionLineaEntity>>()
            rec.lineas.forEach { (p, l) ->
                val previa = porProducto[p.id]
                porProducto[p.id] = if (previa == null) p to l else p to l.copy(cantidad = previa.second.cantidad + l.cantidad)
            }
            val nuevas = lineas.toList()
            lineas.clear()
            porProducto.values.forEach { (p, l) ->
                val añadido = nuevas.firstOrNull { it.producto.id == p.id }?.cantidad?.toIntOrNull() ?: 0
                lineas.add(
                    LineaEntrada(
                        producto = p, coste = l.coste.toString().removeSuffix(".0"), margen = p.margenBeneficio.toString(),
                        cantidad = (l.cantidad + añadido).toString(), cantidadOriginal = l.cantidad, costeOriginal = l.coste
                    )
                )
            }
            nuevas.filter { n -> porProducto.keys.none { it == n.producto.id } }.forEach { lineas.add(it) }
            documentoRecuperado = rec
        }
    }

    /** Cierra el documento recuperado: se quitan las líneas que venían de él (lo añadido a mano se conserva). */
    private fun cerrarRecuperado() {
        if (documentoRecuperado == null) return
        val conservar = lineas.mapNotNull { l ->
            if (l.cantidadOriginal == 0) l
            else {
                val nueva = (l.cantidad.toIntOrNull() ?: 0) - l.cantidadOriginal
                if (nueva > 0) LineaEntrada(l.producto, l.producto.coste.toString(), l.producto.margenBeneficio.toString(), nueva.toString()) else null
            }
        }
        lineas.clear()
        lineas.addAll(conservar)
        documentoRecuperado = null
    }

    /** Descarta la entrada en curso (líneas, documento y proveedor) sin guardar nada. */
    fun cancelar() {
        lineas.clear()
        documento = null
        documentoRecuperado = null
        vistaPrevia = null
        error = null
        seleccionarProveedor(null)
        enFormulario = false
    }

    fun seleccionarProveedor(id: Long?) {
        if (id != proveedorSeleccionadoId) cerrarRecuperado()
        proveedorSeleccionadoId = id
        pedidoOrigenId = null
        // Si el documento ya estaba tecleado antes de elegir proveedor, se busca ahora.
        if (id != null) documento?.let { abrirRecuperado(it) }
    }

    /**
     * Abre un pedido pendiente como entrada: su proveedor y sus líneas (cantidad y coste del pedido,
     * ordenadas por REF) para verificarlas y modificarlas. Al guardar la entrada, el pedido se borra.
     */
    fun abrirPedido(pedido: PedidoEntity) {
        viewModelScope.launch {
            documento = null
            error = null
            val nuevasLineas = pedidoDao.obtenerLineas(pedido.id).mapNotNull { lineaPedido ->
                val producto = productoDao.obtenerPorId(lineaPedido.productoId) ?: return@mapNotNull null
                LineaEntrada(
                    producto = producto,
                    coste = lineaPedido.coste.toString(),
                    margen = producto.margenBeneficio.toString(),
                    cantidad = lineaPedido.cantidad.toString()
                )
            }.sortedBy { it.producto.referenciaFabricante ?: "" }
            lineas.clear()
            lineas.addAll(nuevasLineas)
            proveedorSeleccionadoId = pedido.proveedorId
            pedidoOrigenId = pedido.id
            enFormulario = true
        }
    }

    fun buscar(
        query: String,
        onNoEncontrado: () -> Unit,
        onProveedorDistinto: (ProductoEntity) -> Unit
    ) {
        val texto = query.trim()
        if (texto.isEmpty()) return
        viewModelScope.launch {
            val producto = productoDao.buscarPorCodigoBarrasOReferencia(texto)
            when {
                producto == null -> onNoEncontrado()
                // Existe, pero registrado bajo otro proveedor: no se añade a esta entrada sin más.
                producto.proveedorId != proveedorSeleccionadoId -> onProveedorDistinto(producto)
                else -> agregarOIncrementar(producto)
            }
        }
    }

    private fun agregarOIncrementar(producto: ProductoEntity) {
        val index = lineas.indexOfFirst { it.producto.id == producto.id }
        if (index >= 0) {
            val actual = lineas[index]
            lineas[index] = actual.copy(cantidad = ((actual.cantidad.toIntOrNull() ?: 0) + 1).toString())
        } else {
            lineas.add(
                LineaEntrada(
                    producto = producto,
                    coste = producto.coste.toString(),
                    margen = producto.margenBeneficio.toString(),
                    cantidad = "1"
                )
            )
        }
    }

    fun actualizarLinea(index: Int, coste: String? = null, margen: String? = null, cantidad: String? = null) {
        val actual = lineas.getOrNull(index) ?: return
        lineas[index] = actual.copy(
            coste = coste ?: actual.coste,
            margen = margen ?: actual.margen,
            cantidad = cantidad ?: actual.cantidad
        )
    }

    fun quitarLinea(index: Int) {
        if (index in lineas.indices) lineas.removeAt(index)
    }

    /**
     * Alta rápida de un artículo que no existía. Se crea sin existencia: la cantidad tecleada es lo
     * que se recibe en esta entrada, así que pasa a ser la de su línea y se suma al confirmar.
     */
    fun crearProductoYAgregar(
        sku: String,
        codigoBarras: String,
        referenciaFabricante: String,
        nombre: String,
        categoriaId: Long?,
        proveedorId: Long?,
        coste: String,
        margen: String,
        precioVenta: String,
        existencia: String,
        minimo: String,
        maximo: String,
        mostrarEnTeclado: Boolean,
        onCreado: () -> Unit
    ) {
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) {
            error = "El nombre es obligatorio"
            return
        }
        if (categoriaId == null || proveedorId == null) {
            error = "La categoría y el proveedor son obligatorios"
            return
        }
        val maximoValor = if (maximo.isBlank()) null else maximo.toIntOrNull()
        if (maximo.isNotBlank() && maximoValor == null) {
            error = "Revisa el campo Máximo"
            return
        }
        val costeValor = coste.replace(',', '.').toDoubleOrNull() ?: 0.0
        val margenValor = margen.replace(',', '.').toDoubleOrNull() ?: 0.0
        val pvpValor = precioVenta.replace(',', '.').toDoubleOrNull() ?: 0.0
        val existenciaValor = existencia.toIntOrNull() ?: 0
        viewModelScope.launch {
            val skuLimpio = sku.trim().ifBlank { null }
            val duplicado = productoDao.buscarDuplicado(
                skuLimpio, codigoBarras.trim(), referenciaFabricante.trim()
            )
            if (duplicado != null) {
                error = "Ya existe un artículo con ese SKU, código de barras o REF"
                return@launch
            }
            try {
                val ahora = nowTimestamp()
                val nuevo = ProductoEntity(
                    sku = skuLimpio,
                    codigoBarras = codigoBarras.trim().ifBlank { null },
                    referenciaFabricante = referenciaFabricante.trim().ifBlank { null },
                    nombre = nombreLimpio,
                    categoriaId = categoriaId,
                    proveedorId = proveedorId,
                    coste = costeValor,
                    margenBeneficio = margenValor,
                    precioVenta = pvpValor,
                    existencia = 0,
                    minimo = minimo.toIntOrNull() ?: 0,
                    maximo = maximoValor,
                    mostrarEnTeclado = mostrarEnTeclado,
                    fechaCreacion = ahora,
                    fechaActualizacion = ahora
                )
                val id = db.withTransaction { productoDao.insert(nuevo) }
                error = null
                lineas.add(
                    LineaEntrada(
                        producto = nuevo.copy(id = id),
                        coste = costeValor.toString(),
                        margen = margenValor.toString(),
                        cantidad = if (existenciaValor > 0) existenciaValor.toString() else "1"
                    )
                )
                onCreado()
            } catch (e: SQLiteConstraintException) {
                error = "El SKU o el código de barras ya existen"
            }
        }
    }

    private class LineaValidada(
        val producto: ProductoEntity,
        val cantidad: Int,
        val coste: Double,
        val margen: Double,
        val precioVenta: Double,
        val subtotalConIva: Double
    )

    /** Un albarán/factura del mismo proveedor con el mismo número ya existe: se pregunta si fusionar la entrada con él. */
    data class DuplicadoEntrada(val tipo: TipoDocumento, val numero: String, val fecha: String, val tienePagos: Boolean)

    var duplicado by mutableStateOf<DuplicadoEntrada?>(null)
        private set
    private var alExitoPendiente: (() -> Unit)? = null

    /** Respuesta al aviso de duplicado: true = fusionar, false = guardar aparte, null = cancelar. */
    fun resolverDuplicado(fusionar: Boolean?) {
        val alExito = alExitoPendiente
        duplicado = null
        alExitoPendiente = null
        if (fusionar == null || alExito == null) return
        confirmar(fusionar, alExito)
    }

    /** Aplica los cambios de un documento recuperado: existencias por diferencia, coste/PVP si cambia el coste, líneas y total. */
    private fun guardarRecuperado(validadas: List<LineaValidada>, doc: DatosDocumento, proveedorId: Long, exento: Boolean, onExito: () -> Unit) {
        viewModelScope.launch {
            val ahora = nowTimestamp()
            var mensaje: String? = null
            db.withTransaction {
                val numero = doc.numero.trim()
                val recepcionId = when (doc.tipo) {
                    TipoDocumento.ALBARAN -> recepcionDao.buscarAlbaran(proveedorId, numero)?.recepcionId
                    TipoDocumento.FACTURA -> recepcionDao.buscarFactura(proveedorId, numero)?.recepcionId
                } ?: run { mensaje = "No se encuentra el documento"; return@withTransaction }
                val rec = recepcionDao.obtenerRecepcion(recepcionId) ?: run { mensaje = "No se encuentra la entrada del documento"; return@withTransaction }
                val lineasOriginales = recepcionDao.obtenerLineas(recepcionId)
                val originales = lineasOriginales.groupBy { it.productoId }.mapValues { (_, ls) -> ls.sumOf { it.cantidad } }
                val costeOriginal = lineasOriginales.groupBy { it.productoId }.mapValues { (_, ls) -> ls.first().coste }
                val enEdicion = validadas.associateBy { it.producto.id }
                for ((productoId, antes) in originales) {
                    val p = productoDao.obtenerPorId(productoId) ?: continue
                    val despues = enEdicion[productoId]?.cantidad ?: 0
                    if (p.existencia + (despues - antes) < 0) {
                        mensaje = "\"${p.nombre}\" quedaría con existencia negativa (${p.existencia + (despues - antes)}). Revisa la cantidad."
                        return@withTransaction
                    }
                }
                for ((productoId, antes) in originales) {
                    if (productoId !in enEdicion) productoDao.sumarExistencia(productoId, -antes)
                }
                for (l in validadas) {
                    val delta = l.cantidad - (originales[l.producto.id] ?: 0)
                    if (delta != 0 || l.coste != costeOriginal[l.producto.id]) {
                        productoDao.registrarEntrada(l.producto.id, delta, l.coste, l.margen, l.precioVenta, ahora)
                    }
                }
                recepcionDao.eliminarLineas(recepcionId)
                recepcionDao.insertarLineas(
                    validadas.map { l ->
                        RecepcionLineaEntity(
                            recepcionId = recepcionId, productoId = l.producto.id, cantidad = l.cantidad, coste = l.coste,
                            margenBeneficio = l.margen, subtotalConIva = l.subtotalConIva
                        )
                    }
                )
                recepcionDao.actualizarRecepcion(rec.copy(totalConIva = validadas.sumOf { it.subtotalConIva }))
                val base = validadas.sumOf { it.cantidad * it.coste }
                when (doc.tipo) {
                    TipoDocumento.ALBARAN -> recepcionDao.buscarAlbaran(proveedorId, numero)?.let {
                        recepcionDao.actualizarAlbaran(it.copy(base = base, iva = base * (factorIva(exento) - 1)))
                    }
                    TipoDocumento.FACTURA -> recepcionDao.buscarFactura(proveedorId, numero)?.let {
                        val actualizada = it.copy(base = Math.round(base * 100) / 100.0, iva = Math.round(base * (factorCoste(exento) - 1) * 100) / 100.0)
                        recepcionDao.actualizarFactura(actualizada)
                        PagosMitpv.aplicarAFactura(db, actualizada)
                    }
                }
            }
            if (mensaje != null) {
                error = mensaje
                return@launch
            }
            lineas.clear()
            documento = null
            documentoRecuperado = null
            vistaPrevia = null
            error = null
            pedidoOrigenId = null
            proveedorSeleccionadoId = null
            enFormulario = false
            onExito()
        }
    }

    /** [fusionar] null = comprobar si ya existe un documento igual y, si es así, preguntar antes de guardar. */
    fun confirmar(fusionar: Boolean? = null, onExito: () -> Unit) {
        val proveedorId = proveedorSeleccionadoId
        if (proveedorId == null) {
            error = "Selecciona un proveedor"
            return
        }
        if (lineas.isEmpty()) {
            error = "Añade al menos un artículo"
            return
        }
        val doc = documento
        if (doc == null) {
            error = "Selecciona Albarán o Factura antes de guardar"
            return
        }
        val exento = proveedores.value.firstOrNull { it.id == proveedorId }?.exentoIva == true
        val validadas = mutableListOf<LineaValidada>()
        for (linea in lineas) {
            val cantidad = linea.cantidad.toIntOrNull()
            val coste = linea.coste.replace(',', '.').toDoubleOrNull()
            val margen = linea.margen.replace(',', '.').toDoubleOrNull()
            if (cantidad == null || cantidad <= 0 || coste == null || margen == null) {
                error = "Revisa los datos de \"${linea.producto.nombre}\""
                return
            }
            // Mismas reglas que el formulario de Existencia (coste, margen y PVP se recalculan solo
            // con coste > 0; con coste 0 se respeta el PVP que ya tuviera el artículo):
            // - Artículo con margen definido: se mantiene el margen y el PVP sale del coste nuevo.
            // - Artículo con PVP puesto a mano y sin margen (típico de Existencia sin coste): se
            //   mantiene el PVP y es el margen el que se calcula, para no pisarle el precio.
            val pvpActual = linea.producto.precioVenta
            val (margenFinal, precioVenta) = when {
                coste <= 0 -> margen to pvpActual
                margen == 0.0 && pvpActual > 0 -> (((pvpActual / (coste * factorCoste(exento))) - 1) * 100) to pvpActual
                else -> margen to ProductoEntity.calcularPrecioVenta(coste, margen, exento)
            }
            // Lo que factura el proveedor incluye IVA y recargo de equivalencia.
            validadas.add(
                LineaValidada(
                    linea.producto, cantidad, coste, margenFinal, precioVenta, cantidad * coste * factorCoste(exento)
                )
            )
        }

        // Documento recuperado y abierto: se guardan los cambios sobre él (no una entrada nueva).
        if (documentoRecuperado?.let { it.tipo == doc.tipo && it.numero == doc.numero.trim() } == true) {
            guardarRecuperado(validadas, doc, proveedorId, exento, onExito)
            return
        }
        val fusionarEfectivo = fusionar
        viewModelScope.launch {
            val numeroDoc = doc.numero.trim()
            var albaranExistente: AlbaranEntity? = null
            var facturaExistente: FacturaEntity? = null
            if (numeroDoc.isNotEmpty() && fusionarEfectivo != false) {
                when (doc.tipo) {
                    TipoDocumento.ALBARAN -> albaranExistente = recepcionDao.buscarAlbaran(proveedorId, numeroDoc)
                    TipoDocumento.FACTURA -> facturaExistente = recepcionDao.buscarFactura(proveedorId, numeroDoc)
                }
                val hayPagos = facturaExistente?.let { db.pagoFacturaDao().obtenerPorFactura(it.id).isNotEmpty() } ?: false
                if (albaranExistente != null || facturaExistente != null) {
                    if (fusionarEfectivo == null) {
                        duplicado = DuplicadoEntrada(
                            doc.tipo, numeroDoc, albaranExistente?.fecha ?: facturaExistente!!.fecha, hayPagos
                        )
                        alExitoPendiente = onExito
                        return@launch
                    }
                    // Una factura con pagos ya registrados no se toca: se guarda aparte.
                    if (hayPagos) { facturaExistente = null }
                }
            }
            val albExist = albaranExistente
            val facExist = facturaExistente
            val ahora = nowTimestamp()
            db.withTransaction {
                for (linea in validadas) {
                    productoDao.registrarEntrada(
                        productoId = linea.producto.id,
                        cantidad = linea.cantidad,
                        coste = linea.coste,
                        margen = linea.margen,
                        precioVenta = linea.precioVenta,
                        fecha = ahora
                    )
                }
                val totalNuevo = validadas.sumOf { it.subtotalConIva }
                // Fusión: las líneas pasan a la recepción del documento existente y su total crece.
                val recepcionExistenteId = albExist?.recepcionId ?: facExist?.recepcionId
                val recepcionId = if (recepcionExistenteId != null) {
                    recepcionDao.obtenerRecepcion(recepcionExistenteId)?.let {
                        recepcionDao.actualizarRecepcion(it.copy(totalConIva = it.totalConIva + totalNuevo))
                    }
                    recepcionExistenteId
                } else {
                    recepcionDao.insertarRecepcion(RecepcionEntity(proveedorId = proveedorId, fecha = ahora, totalConIva = totalNuevo))
                }
                recepcionDao.insertarLineas(
                    validadas.map { linea ->
                        RecepcionLineaEntity(
                            recepcionId = recepcionId,
                            productoId = linea.producto.id,
                            cantidad = linea.cantidad,
                            coste = linea.coste,
                            margenBeneficio = linea.margen,
                            subtotalConIva = linea.subtotalConIva
                        )
                    }
                )
                val base = doc.base.replace(',', '.').toDoubleOrNull()
                val iva = doc.iva.replace(',', '.').toDoubleOrNull()
                // Una factura sin base ni IVA tecleados no se queda a 0: se toman del total de esta entrada
                // (como hace el listado de albaranes), para que el importe a pagar sea el real.
                val exentoProv = proveedorId?.let { id -> db.proveedorDao().obtenerTodos().firstOrNull { it.id == id }?.exentoIva } == true
                val sinImportes = (base ?: 0.0) == 0.0 && (iva ?: 0.0) == 0.0
                val baseFactura = if (sinImportes) Math.round(totalNuevo / factorCoste(exentoProv) * 100) / 100.0 else base ?: 0.0
                val ivaFactura = if (sinImportes) Math.round((totalNuevo - baseFactura) * 100) / 100.0 else iva ?: 0.0
                if (albExist != null) {
                    recepcionDao.actualizarAlbaran(
                        albExist.copy(
                            recepcionId = recepcionId,
                            base = if (base == null && albExist.base == null) null else (albExist.base ?: 0.0) + (base ?: 0.0),
                            iva = if (iva == null && albExist.iva == null) null else (albExist.iva ?: 0.0) + (iva ?: 0.0)
                        )
                    )
                } else if (facExist != null) {
                    val fusionada = facExist.copy(
                        recepcionId = recepcionId, base = facExist.base + baseFactura, iva = facExist.iva + ivaFactura
                    )
                    recepcionDao.actualizarFactura(fusionada)
                    PagosMitpv.aplicarAFactura(db, fusionada)
                } else when (doc.tipo) {
                    TipoDocumento.ALBARAN -> recepcionDao.insertarAlbaran(
                        AlbaranEntity(recepcionId = recepcionId, numero = doc.numero.ifBlank { null }, fecha = fechaIso(doc.fecha), base = base, iva = iva)
                    )
                    TipoDocumento.FACTURA -> {
                        val factura = FacturaEntity(
                            recepcionId = recepcionId, numero = doc.numero.ifBlank { null }, fecha = fechaIso(doc.fecha),
                            base = baseFactura, iva = ivaFactura
                        )
                        val facturaId = recepcionDao.insertarFactura(factura)
                        // Si MiTPV ya pagó esta factura en efectivo, queda pagada con la fecha de ese pago.
                        PagosMitpv.aplicarAFactura(db, factura.copy(id = facturaId))
                    }
                }
                pedidoOrigenId?.let { pedidoDao.eliminarPorId(it) }
            }
            lineas.clear()
            documento = null
            documentoRecuperado = null
            vistaPrevia = null
            error = null
            pedidoOrigenId = null
            proveedorSeleccionadoId = null
            enFormulario = false
            onExito()
        }
    }
}
