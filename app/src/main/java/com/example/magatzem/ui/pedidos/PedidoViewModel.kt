package com.example.magatzem.ui.pedidos

import com.example.magatzem.data.ESTADO_PEDIDO_PENDIENTE
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.factorCoste
import com.example.magatzem.data.factorIva
import com.example.magatzem.data.factorRecargo
import com.example.magatzem.data.PedidoEntity
import com.example.magatzem.ui.common.esFechaValida
import com.example.magatzem.ui.common.fechaIso
import com.example.magatzem.ui.documentos.AvisoRemarcar
import com.example.magatzem.ui.documentos.DocumentoMotor
import com.example.magatzem.ui.documentos.TipoDoc
import com.example.magatzem.data.PedidoLineaEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private fun nowTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

/** Importe base (sin impuestos) y su desglose de recargo de equivalencia, IVA y total. */
data class ResumenPedido(val importe: Double, val importeRecargo: Double, val importeIva: Double, val total: Double) {
    companion object {
        fun de(importe: Double, exentoIva: Boolean = false) = ResumenPedido(
            importe = importe,
            importeRecargo = importe * factorRecargo(exentoIva),
            importeIva = importe * (factorIva(exentoIva) - 1),
            total = importe * factorCoste(exentoIva)
        )
    }
}


/**
 * Pedidos a proveedor. Se elige el proveedor: si tiene pedidos pendientes se listan (al pulsar uno se abre), y "Nuevo"
 * empieza otro. Dentro de un pedido se marcan los artículos a pedir con su cantidad y TODO SE GUARDA SOLO, al momento:
 * no hay botón de guardar. Un pedido que se queda sin líneas se borra.
 */
class PedidoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val pedidoDao = db.pedidoDao()

    /** Todos los pedidos (el listado de Oficina y los pendientes del proveedor elegido salen de aquí). */
    val pedidos: StateFlow<List<PedidoEntity>> = pedidoDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proveedores: StateFlow<List<ProveedorEntity>> = db.proveedorDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorias: StateFlow<List<CategoriaEntity>> = db.categoriaDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productos: StateFlow<List<ProductoEntity>> = db.productoDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var proveedorSeleccionadoId by mutableStateOf<Long?>(null)
        private set

    /** false = se ven los pedidos pendientes del proveedor; true = dentro de un pedido (nuevo o abierto). */
    var enPedido by mutableStateOf(false)
        private set

    /**
     * Unidades a pedir por producto (texto editable). Se rellena con la sugerencia (máximo - existencia,
     * o "0" si el producto no tiene máximo) la primera vez que aparece, y no se vuelve a pisar aunque el
     * usuario la cambie a mano.
     */
    val unidades = mutableStateMapOf<Long, String>()

    /** Productos que están en el pedido, con sus unidades (lo guardado). */
    val seleccionados = mutableStateMapOf<Long, Int>()

    /** Los artículos que ya estaban cuando se abrió el pedido: salen primero, aparte de las categorías. */
    var inicialesIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** Pedido que se está guardando (null hasta que se añade la primera línea de uno nuevo). */
    var pedidoEditando by mutableStateOf<PedidoEntity?>(null)
        private set

    /** Coste con el que quedó cada línea al guardarla (las líneas ya guardadas conservan el suyo). */
    private val costes = mutableMapOf<Long, Double>()
    private val guardado = Mutex()

    private fun limpiar() {
        unidades.clear()
        seleccionados.clear()
        costes.clear()
        inicialesIds = emptySet()
        pedidoEditando = null
    }

    fun seleccionarProveedor(id: Long?) {
        proveedorSeleccionadoId = id
        enPedido = false
        limpiar()
    }

    /** Botón "Nuevo": pedido en blanco del proveedor elegido. */
    fun nuevoPedido() {
        if (proveedorSeleccionadoId == null) return
        limpiar()
        enPedido = true
    }

    /** Abre un pedido guardado (desde la lista de pendientes o desde Oficina): sus líneas y proveedor. */
    fun abrirPedido(pedidoId: Long) {
        viewModelScope.launch {
            val pedido = pedidoDao.obtenerPorId(pedidoId) ?: return@launch
            val lineas = pedidoDao.obtenerLineas(pedidoId)
            limpiar()
            lineas.forEach { linea ->
                seleccionados[linea.productoId] = linea.cantidad
                unidades[linea.productoId] = linea.cantidad.toString()
                costes[linea.productoId] = linea.coste
            }
            inicialesIds = lineas.map { it.productoId }.toSet()
            proveedorSeleccionadoId = pedido.proveedorId
            pedidoEditando = pedido
            enPedido = true
        }
    }

    /** Volver desde dentro de un pedido a la lista de pendientes del proveedor. */
    fun salirDePedido() {
        enPedido = false
        limpiar()
    }

    /** "Borrar" de una fila del listado de Oficina: borra el pedido (y sus líneas, en cascada). */
    fun eliminar(pedido: PedidoEntity) {
        viewModelScope.launch { pedidoDao.eliminar(pedido) }
    }

    fun sugerencia(producto: ProductoEntity): String {
        val maximo = producto.maximo ?: return "0"
        return (maximo - producto.existencia).coerceAtLeast(0).toString()
    }

    /** Cambia las unidades de una fila; si el artículo ya está en el pedido y la cantidad es válida, se guarda al momento. */
    fun actualizarUnidades(productoId: Long, valor: String) {
        unidades[productoId] = valor
        val cantidad = valor.toIntOrNull()
        if (productoId in seleccionados && cantidad != null && cantidad > 0 && seleccionados[productoId] != cantidad) {
            seleccionados[productoId] = cantidad
            guardarAhora()
        }
    }

    /** Añade el artículo al pedido (con las unidades que haya en su campo) o lo quita; se guarda al momento. */
    fun agregar(productoId: Long) {
        if (seleccionados.containsKey(productoId)) {
            seleccionados.remove(productoId)
            costes.remove(productoId)
        } else {
            val cantidad = unidades[productoId]?.toIntOrNull()?.takeIf { it > 0 } ?: 1
            seleccionados[productoId] = cantidad
            unidades[productoId] = cantidad.toString()
            productos.value.firstOrNull { it.id == productoId }?.let { costes[productoId] = it.coste }
        }
        guardarAhora()
    }

    /** ¿El proveedor del pedido va sin IVA ni recargo (sellos de Correos)? */
    private fun proveedorExento(): Boolean = proveedores.value.firstOrNull { it.id == proveedorSeleccionadoId }?.exentoIva == true

    /** Resumen en vivo (importe/recargo/IVA/total) del pedido tal como está. */
    fun resumen(): ResumenPedido {
        val importe = seleccionados.entries.sumOf { (productoId, cantidad) -> (costes[productoId] ?: 0.0) * cantidad }
        return ResumenPedido.de(importe, proveedorExento())
    }

    /** Escribe en la base de datos el pedido tal como está ahora (sin botón: se llama tras cada cambio). */
    private fun guardarAhora() {
        val proveedorId = proveedorSeleccionadoId ?: return
        val lineas = seleccionados.map { (productoId, cantidad) ->
            val coste = costes.getOrPut(productoId) { productos.value.firstOrNull { it.id == productoId }?.coste ?: 0.0 }
            Triple(productoId, cantidad, coste)
        }
        val resumen = ResumenPedido.de(lineas.sumOf { (_, cantidad, coste) -> cantidad * coste }, proveedorExento())
        viewModelScope.launch {
            guardado.withLock {
                db.withTransaction {
                    val actual = pedidoEditando
                    if (lineas.isEmpty()) {
                        // Sin líneas no hay pedido: se borra el que hubiera.
                        actual?.let { pedidoDao.eliminar(it) }
                        pedidoEditando = null
                        return@withTransaction
                    }
                    val pedido = if (actual == null) {
                        val nuevo = PedidoEntity(
                            proveedorId = proveedorId, fecha = nowTimestamp(), importe = resumen.importe,
                            importeRecargo = resumen.importeRecargo, importeIva = resumen.importeIva, total = resumen.total
                        )
                        nuevo.copy(id = pedidoDao.insertarPedido(nuevo))
                    } else {
                        actual.copy(
                            importe = resumen.importe, importeRecargo = resumen.importeRecargo,
                            importeIva = resumen.importeIva, total = resumen.total
                        ).also { pedidoDao.actualizar(it) }
                    }
                    pedidoDao.eliminarLineas(pedido.id)
                    pedidoDao.insertarLineas(
                        lineas.map { (productoId, cantidad, coste) ->
                            PedidoLineaEntity(pedidoId = pedido.id, productoId = productoId, cantidad = cantidad, coste = coste, subtotal = cantidad * coste)
                        }
                    )
                    pedidoEditando = pedido
                }
            }
        }
    }

    /** Pasar el pedido a albarán o factura: ya existe uno con ese número del proveedor (se pregunta si añadir a él). */
    var duplicadoPase by mutableStateOf<Triple<TipoDoc, String, String>?>(null)
        private set
    var errorPase by mutableStateOf<String?>(null)
        private set

    /** Artículos que hay que remarcar tras pasar el pedido (ver [AvisoRemarcar]); el documento se abre al cerrar el aviso. */
    var avisosPase by mutableStateOf<List<AvisoRemarcar>>(emptyList())
        private set
    private var alCerrarAvisosPase: (() -> Unit)? = null

    fun cerrarAvisosPase() {
        val seguir = alCerrarAvisosPase
        alCerrarAvisosPase = null
        avisosPase = emptyList()
        seguir?.invoke()
    }

    fun cancelarDuplicadoPase() {
        duplicadoPase = null
    }

    fun limpiarErrorPase() {
        errorPase = null
    }

    /**
     * El pedido pasa a albarán/factura: sus líneas entran como líneas del documento (la existencia sube por cada una) y el pedido
     * desaparece. El documento se abre después para poder editarlo. Con un documento ya existente del mismo número, [fusionar]
     * true suma las cantidades a las líneas que ya tuviera y añade las nuevas; null pregunta antes.
     */
    fun pasarA(tipo: TipoDoc, numero: String, fecha: String, fusionar: Boolean?, onHecho: (Long) -> Unit) {
        val pedido = pedidoEditando ?: return
        val prov = proveedorSeleccionadoId ?: return
        if (!esFechaValida(fecha.trim())) { errorPase = "La fecha no es válida (dd-MM-aaaa)"; return }
        val lineasPedido = seleccionados.map { (id, cantidad) -> Triple(id, cantidad, costes[id] ?: 0.0) }
        if (lineasPedido.isEmpty()) return
        viewModelScope.launch {
            guardado.withLock {
                val motor = DocumentoMotor(db)
                val limpio = numero.trim()
                val existente = if (limpio.isNotEmpty()) motor.buscarPorNumero(tipo, prov, limpio) else null
                if (existente != null && fusionar == null) {
                    duplicadoPase = Triple(tipo, numero, fecha)
                    return@withLock
                }
                duplicadoPase = null
                val docId = existente ?: motor.crear(tipo, prov, limpio, fechaIso(fecha))
                val doc = motor.abrir(tipo, docId) ?: return@withLock
                val previas = motor.lineas(doc.recepcionId).associateBy { it.producto.id }
                val remarcar = mutableListOf<AvisoRemarcar>()
                for ((productoId, cantidad, coste) in lineasPedido) {
                    val previa = previas[productoId]
                    val resultado = motor.guardarLinea(doc.recepcionId, productoId, (previa?.cantidad ?: 0) + cantidad, previa?.coste ?: coste)
                    if (resultado.error != null) { errorPase = resultado.error; return@withLock }
                    resultado.remarcar?.let { remarcar += it }
                }
                // El pedido ya está atendido.
                pedidoDao.eliminarPorId(pedido.id)
                pedidoEditando = null
                enPedido = false
                limpiar()
                if (remarcar.isEmpty()) onHecho(docId) else {
                    // Antes de abrir el documento se avisa de lo que hay que remarcar.
                    avisosPase = remarcar
                    alCerrarAvisosPase = { onHecho(docId) }
                }
            }
        }
    }
}
