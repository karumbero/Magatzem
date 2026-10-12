package com.example.magatzem.ui.documentos

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.ocr.OcrFactura
import com.example.magatzem.ocr.ParserFactura
import com.example.magatzem.ocr.ProdInfo
import com.example.magatzem.ocr.ProvInfo
import com.example.magatzem.ui.common.esFechaValida
import com.example.magatzem.ui.common.fechaIso
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Factura que ya está en Magatzem con el mismo proveedor y número: sirve para comprobar la lectura sin duplicar nada. */
class FacturaExistente(val lineas: Map<Long, Pair<Int, Double>>, val total: Double)

enum class FaseEscaner { INICIO, LEYENDO, REVISAR }

/** Una línea reconocida en la factura, editable antes de crear nada. */
class LineaRevision(val producto: ProdInfo, incluida: Boolean, cantidad: String, coste: String, val cuadra: Boolean, val importeLeido: Double?, val texto: String) {
    var incluida by mutableStateOf(incluida)
    var cantidad by mutableStateOf(cantidad)
    var coste by mutableStateOf(coste)
    val subtotal: Double get() = (cantidad.trim().toIntOrNull() ?: 0) * (coste.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)
}

/**
 * Escanear factura: foto o PDF → texto (OCR en el aparato) → datos propuestos (proveedor por NIF, número, fecha, líneas por REF)
 * que se REVISAN y corrigen → al confirmar se crea la factura con sus líneas, igual que si se hubieran tecleado.
 */
class EscanearFacturaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val motor = DocumentoMotor(db)

    val proveedores: StateFlow<List<ProveedorEntity>> = db.proveedorDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var fase by mutableStateOf(FaseEscaner.INICIO)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var proveedorId by mutableStateOf<Long?>(null)
    var numero by mutableStateOf("")
    var fecha by mutableStateOf("")                 // dd-MM-aaaa
    var leidoBase by mutableStateOf<Double?>(null)
        private set
    var leidoIva by mutableStateOf<Double?>(null)
        private set
    var leidoTotal by mutableStateOf<Double?>(null)
        private set
    var textoCompleto by mutableStateOf("")
        private set
    val lineas = mutableStateListOf<LineaRevision>()
    val sinReconocer = mutableStateListOf<String>()
    var trabajando by mutableStateOf(false)
        private set
    var existente by mutableStateOf<FacturaExistente?>(null)
        private set

    fun reiniciar() {
        fase = FaseEscaner.INICIO; mensaje = null; lineas.clear(); sinReconocer.clear()
        proveedorId = null; numero = ""; fecha = ""; leidoBase = null; leidoIva = null; leidoTotal = null; textoCompleto = ""; existente = null
    }

    fun leer(uri: Uri) {
        fase = FaseEscaner.LEYENDO; mensaje = null
        viewModelScope.launch {
            try {
                val filas = OcrFactura.leerFilas(getApplication(), uri)
                if (filas.isEmpty()) { fase = FaseEscaner.INICIO; mensaje = "No se ha podido leer ningún texto. Prueba con más luz, la hoja plana y la cámara quieta."; return@launch }
                val provs = db.proveedorDao().obtenerTodos().map { ProvInfo(it.id, it.nombre, it.nifCif) }
                val prods = db.productoDao().obtenerTodosOrdenados().map { ProdInfo(it.id, it.sku, it.referenciaFabricante, it.nombre, it.proveedorId) }
                val propio = db.datosEmpresaDao().obtener()?.nifCif
                val r = ParserFactura.interpretar(filas, provs, propio, prods)
                textoCompleto = filas.joinToString("\\n")
                proveedorId = r.proveedor?.id
                numero = r.numero.orEmpty()
                fecha = r.fechaIso?.let { it.substring(8) + "-" + it.substring(5, 7) + "-" + it.substring(0, 4) }.orEmpty()
                leidoBase = r.base; leidoIva = r.iva; leidoTotal = r.total
                lineas.clear(); sinReconocer.clear()
                r.lineas.forEach { l -> lineas += LineaRevision(l.producto, true, l.cantidad.toString(), formato(l.coste), l.cuadra, l.importe, l.texto) }
                sinReconocer += r.sinReconocer
                fase = FaseEscaner.REVISAR
            } catch (e: Exception) {
                fase = FaseEscaner.INICIO
                mensaje = "No se ha podido leer la factura: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** ¿Ya existe una factura de este proveedor con este número? Entonces no se puede crear otra, pero se compara con ella. */
    fun comprobarExistente() {
        val prov = proveedorId; val num = numero.trim()
        viewModelScope.launch {
            existente = if (prov != null && num.isNotEmpty()) {
                motor.buscarPorNumero(TipoDoc.FACTURA, prov, num)?.let { id ->
                    motor.abrir(TipoDoc.FACTURA, id)?.let { doc ->
                        val ls = motor.lineas(doc.recepcionId)
                        FacturaExistente(ls.associate { it.producto.id to (it.cantidad to it.coste) }, ls.sumOf { it.cantidad * it.coste })
                    }
                }
            } else null
        }
    }

    private fun formato(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    /** Crea la factura con las líneas marcadas. Devuelve (proveedor, factura, avisos de remarcar) a quien llama. */
    fun crear(onCreada: (Long, Long, List<AvisoRemarcar>) -> Unit) {
        val prov = proveedorId
        if (prov == null) { mensaje = "Elige el proveedor."; return }
        if (!esFechaValida(fecha.trim())) { mensaje = "La fecha no es válida (dd-MM-aaaa)."; return }
        val elegidas = lineas.filter { it.incluida }
        if (elegidas.isEmpty()) { mensaje = "No hay ninguna línea marcada."; return }
        val malas = elegidas.filter { (it.cantidad.trim().toIntOrNull() ?: 0) <= 0 || it.coste.trim().replace(',', '.').toDoubleOrNull() == null }
        if (malas.isNotEmpty()) { mensaje = "Revisa la cantidad y el coste de: " + malas.joinToString { it.producto.sku ?: it.producto.nombre }; return }
        trabajando = true; mensaje = null
        viewModelScope.launch {
            try {
                val num = numero.trim()
                if (num.isNotEmpty() && motor.buscarPorNumero(TipoDoc.FACTURA, prov, num) != null) {
                    mensaje = "Ya existe la factura $num de ese proveedor: ábrela desde la lista en vez de crearla otra vez."
                    return@launch
                }
                val docId = motor.crear(TipoDoc.FACTURA, prov, num, fechaIso(fecha))
                val doc = motor.abrir(TipoDoc.FACTURA, docId) ?: error("No se pudo abrir la factura creada")
                val avisos = mutableListOf<AvisoRemarcar>()
                val errores = mutableListOf<String>()
                elegidas.forEach { l ->
                    val r = motor.guardarLinea(doc.recepcionId, l.producto.id, l.cantidad.trim().toInt(), l.coste.trim().replace(',', '.').toDouble())
                    r.error?.let { errores += "${l.producto.sku ?: l.producto.nombre}: $it" }
                    r.remarcar?.let { avisos += it }
                }
                onCreada(prov, docId, avisos)
                if (errores.isNotEmpty()) mensaje = "Factura creada, pero algunas líneas dieron error: " + errores.joinToString("; ")
                reiniciar()
            } catch (e: Exception) {
                mensaje = "No se pudo crear la factura: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                trabajando = false
            }
        }
    }
}
