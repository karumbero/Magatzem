package com.example.magatzem.ui.documentos

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
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.ESTADO_FACTURA_PENDIENTE
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.data.factorCoste
import com.example.magatzem.data.factorIva
import com.example.magatzem.data.factorRecargo
import com.example.magatzem.ui.common.esFechaValida
import com.example.magatzem.ui.common.fechaIso
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Una línea en pantalla: lo tecleado (texto) de un artículo del documento. */
class LineaDoc(val producto: ProductoEntity, cantidad: String, coste: String) {
    var cantidad by mutableStateOf(cantidad)
    var coste by mutableStateOf(coste)
}

/** Un documento pendiente de la lista del proveedor. */
class DocResumen(val id: Long, val numero: String, val fecha: String, val total: Double)

/**
 * Pantalla de Albarán o Factura (misma mecánica que Pedido): se elige proveedor, se listan sus documentos pendientes, Nuevo
 * crea uno (pide número y fecha), y dentro todo se guarda al momento, sin botón. Abrir uno existente permite añadir y
 * modificar líneas por si se cerró por error. Ver [DocumentoMotor] para cómo se aplican los cambios.
 */
class DocumentoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val motor = DocumentoMotor(db)
    private val productoDao = db.productoDao()

    private val tipoFlow = MutableStateFlow(TipoDoc.ALBARAN)
    private val proveedorFlow = MutableStateFlow<Long?>(null)

    val tipo: TipoDoc get() = tipoFlow.value

    val proveedores: StateFlow<List<ProveedorEntity>> = db.proveedorDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categorias: StateFlow<List<CategoriaEntity>> = db.categoriaDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var proveedorId by mutableStateOf<Long?>(null)
        private set
    var abierto by mutableStateOf<DocAbierto?>(null)
        private set
    val lineas = mutableStateListOf<LineaDoc>()
    var error by mutableStateOf<String?>(null)
        private set
    private val guardado = Mutex()

    fun limpiarError() {
        error = null
    }

    fun iniciar(t: TipoDoc) {
        if (tipo != t) { cerrar(); tipoFlow.value = t; proveedorId = null; proveedorFlow.value = null }
    }

    /** Documentos pendientes del proveedor elegido (albaranes sin pasar a factura / facturas por pagar). */
    val pendientes: StateFlow<List<DocResumen>> = combine(
        db.recepcionDao().observeAlbaranes(), db.recepcionDao().observeFacturas(), db.recepcionDao().observeRecepciones(),
        tipoFlow, proveedorFlow
    ) { albaranes, facturas, recepciones, t, prov ->
        val recPorId = recepciones.associateBy { it.id }
        val lista = if (t == TipoDoc.ALBARAN) albaranes.mapNotNull { a ->
            recPorId[a.recepcionId]?.takeIf { it.proveedorId == prov }?.let { DocResumen(a.id, a.numero.orEmpty(), a.fecha, it.totalConIva) }
        } else facturas.filter { it.estado == ESTADO_FACTURA_PENDIENTE }.mapNotNull { f ->
            recPorId[f.recepcionId]?.takeIf { it.proveedorId == prov }?.let { DocResumen(f.id, f.numero.orEmpty(), f.fecha, it.totalConIva) }
        }
        lista.sortedByDescending { it.fecha }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun seleccionarProveedor(id: Long?) {
        cerrar()
        proveedorId = id
        proveedorFlow.value = id
    }

    private fun cerrar() {
        val a = abierto
        abierto = null
        lineas.clear()
        error = null
        if (a != null) viewModelScope.launch { motor.borrarSiVacio(a.tipo, a.docId) }
    }

    /** Volver desde dentro de un documento a la lista de pendientes. */
    fun salirDeDocumento() {
        cerrar()
    }

    /** Nuevo: crea el documento (número y fecha dd-MM-aaaa). Si ya existe uno igual del proveedor, se recupera. */
    fun nuevo(numero: String, fecha: String, onAbierto: () -> Unit = {}) {
        val prov = proveedorId ?: return
        if (!esFechaValida(fecha.trim())) { error = "La fecha no es válida (dd-MM-aaaa)"; return }
        viewModelScope.launch {
            val limpio = numero.trim()
            val existente = if (limpio.isNotEmpty()) motor.buscarPorNumero(tipo, prov, limpio) else null
            val id = existente ?: motor.crear(tipo, prov, limpio, fechaIso(fecha))
            cargar(id)
            if (existente != null) error = "Ya existía ${tipo.nombre.lowercase()} $limpio: se ha recuperado."
            onAbierto()
        }
    }

    /** Abre un documento guardado: sus líneas (una por artículo) para seguir entrando o modificar. */
    fun abrir(docId: Long) {
        viewModelScope.launch { cargar(docId) }
    }

    private suspend fun cargar(docId: Long) {
        val doc = motor.abrir(tipo, docId) ?: return
        if (tipo == TipoDoc.FACTURA && motor.facturaConPagos(docId)) {
            error = "Esta factura tiene pagos o un plan de pago: anúlalo antes de modificarla."
            return
        }
        lineas.clear()
        motor.lineas(doc.recepcionId).forEach { l ->
            lineas.add(LineaDoc(l.producto, l.cantidad.toString(), formato(l.coste)))
        }
        proveedorId = doc.proveedorId
        proveedorFlow.value = doc.proveedorId
        abierto = doc
        error = null
    }

    private fun formato(v: Double) = if (v == 0.0) "0" else v.toString().removeSuffix(".0")
    private fun cantidadDe(l: LineaDoc): Int? = l.cantidad.trim().toIntOrNull()?.takeIf { it > 0 }
    private fun costeDe(l: LineaDoc): Double? = l.coste.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

    /** Se llama tras teclear en una línea: si lo tecleado es válido, se guarda al momento (solo esa línea). */
    fun lineaCambiada(linea: LineaDoc) {
        if (cantidadDe(linea) == null || costeDe(linea) == null) return
        guardarLinea(linea.producto.id)
    }

    private fun guardarLinea(productoId: Long) {
        val doc = abierto ?: return
        viewModelScope.launch {
            guardado.withLock {
                // Se lee lo que hay en pantalla AHORA (no lo de cuando se pulsó), para no aplicar valores ya superados.
                val l = lineas.firstOrNull { it.producto.id == productoId } ?: return@withLock
                val cantidad = cantidadDe(l) ?: return@withLock
                val coste = costeDe(l) ?: return@withLock
                val mensaje = motor.guardarLinea(doc.recepcionId, productoId, cantidad, coste)
                if (mensaje != null) {
                    error = mensaje
                    recargar(doc)
                } else error = null
            }
        }
    }

    /** Vuelve a leer las líneas guardadas (tras un rechazo, para que la pantalla muestre lo que de verdad hay). */
    private suspend fun recargar(doc: DocAbierto) {
        val guardadas = motor.lineas(doc.recepcionId)
        lineas.clear()
        guardadas.forEach { lineas.add(LineaDoc(it.producto, it.cantidad.toString(), formato(it.coste))) }
    }

    fun quitar(linea: LineaDoc) {
        val doc = abierto ?: return
        viewModelScope.launch {
            guardado.withLock {
                val mensaje = motor.quitarLinea(doc.recepcionId, linea.producto.id)
                if (mensaje != null) { error = mensaje; recargar(doc) } else { error = null; lineas.removeAll { it.producto.id == linea.producto.id } }
            }
        }
    }

    /** Escáner / SKU / REF: si el artículo ya está en el documento suma 1 a su línea; si no, la crea con 1 unidad. */
    fun buscar(query: String, onNoEncontrado: () -> Unit) {
        val texto = query.trim()
        if (texto.isEmpty() || abierto == null) return
        viewModelScope.launch {
            val p = productoDao.buscarPorCodigoBarrasOReferencia(texto)
            when {
                p == null -> onNoEncontrado()
                p.proveedorId != proveedorId -> error = "\"${p.nombre}\" es de otro proveedor."
                else -> {
                    val existente = lineas.firstOrNull { it.producto.id == p.id }
                    if (existente != null) {
                        existente.cantidad = ((cantidadDe(existente) ?: 0) + 1).toString()
                    } else {
                        lineas.add(LineaDoc(p, "1", formato(p.coste)))
                    }
                    error = null
                    guardarLinea(p.id)
                }
            }
        }
    }

    /** Alta rápida de un artículo que no existía (se crea sin existencia: la cantidad entra por el documento). */
    fun crearProductoYAgregar(
        sku: String, codigoBarras: String, referenciaFabricante: String, nombre: String, categoriaId: Long?,
        proveedor: Long?, coste: String, margen: String, precioVenta: String, existencia: String, minimo: String,
        maximo: String, mostrarEnTeclado: Boolean, onCreado: () -> Unit
    ) {
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) { error = "El nombre es obligatorio"; return }
        if (categoriaId == null || proveedor == null) { error = "La categoría y el proveedor son obligatorios"; return }
        val maximoValor = if (maximo.isBlank()) null else maximo.toIntOrNull()
        if (maximo.isNotBlank() && maximoValor == null) { error = "Revisa el campo Máximo"; return }
        val costeValor = coste.replace(',', '.').toDoubleOrNull() ?: 0.0
        val cantidad = existencia.toIntOrNull()?.takeIf { it > 0 } ?: 1
        viewModelScope.launch {
            val skuLimpio = sku.trim().ifBlank { null }
            if (productoDao.buscarDuplicado(skuLimpio, codigoBarras.trim(), referenciaFabricante.trim()) != null) {
                error = "Ya existe un artículo con ese SKU, código de barras o REF"
                return@launch
            }
            try {
                val ahora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val nuevo = ProductoEntity(
                    sku = skuLimpio, codigoBarras = codigoBarras.trim().ifBlank { null },
                    referenciaFabricante = referenciaFabricante.trim().ifBlank { null }, nombre = nombreLimpio,
                    categoriaId = categoriaId, proveedorId = proveedor, coste = costeValor,
                    margenBeneficio = margen.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    precioVenta = precioVenta.replace(',', '.').toDoubleOrNull() ?: 0.0, existencia = 0,
                    minimo = minimo.toIntOrNull() ?: 0, maximo = maximoValor, mostrarEnTeclado = mostrarEnTeclado,
                    fechaCreacion = ahora, fechaActualizacion = ahora
                )
                val id = db.withTransaction { productoDao.insert(nuevo) }
                lineas.add(LineaDoc(nuevo.copy(id = id), cantidad.toString(), formato(costeValor)))
                error = null
                guardarLinea(id)
                onCreado()
            } catch (e: SQLiteConstraintException) {
                error = "El SKU o el código de barras ya existen"
            }
        }
    }

    // --- Importes en vivo (de lo que hay en pantalla) ---
    private fun exento(): Boolean = proveedores.value.firstOrNull { it.id == proveedorId }?.exentoIva == true
    val base: Double get() = lineas.sumOf { (cantidadDe(it) ?: 0) * (costeDe(it) ?: 0.0) }
    val iva: Double get() = base * (factorIva(exento()) - 1)
    val recargo: Double get() = base * factorRecargo(exento())
    val total: Double get() = base * factorCoste(exento())

    /** Albarán → factura (número y fecha de la factura nuevos). Devuelve por el callback el id de la factura. */
    var duplicadoFactura by mutableStateOf<Pair<String, String>?>(null)
        private set

    fun pasarAFactura(numero: String, fecha: String, fusionar: Boolean?, onFactura: (Long) -> Unit) {
        val doc = abierto ?: return
        if (doc.tipo != TipoDoc.ALBARAN) return
        if (!esFechaValida(fecha.trim())) { error = "La fecha no es válida (dd-MM-aaaa)"; return }
        viewModelScope.launch {
            val prov = doc.proveedorId
            val existe = prov != null && numero.isNotBlank() && motor.buscarPorNumero(TipoDoc.FACTURA, prov, numero.trim()) != null
            if (existe && fusionar == null) {
                duplicadoFactura = numero to fecha
                return@launch
            }
            duplicadoFactura = null
            guardado.withLock {
                val id = motor.albaranAFactura(doc.docId, numero.trim(), fechaIso(fecha), fusionar == true)
                if (id != null) {
                    abierto = null
                    lineas.clear()
                    onFactura(id)
                }
            }
        }
    }

    fun cancelarDuplicadoFactura() {
        duplicadoFactura = null
    }
}
