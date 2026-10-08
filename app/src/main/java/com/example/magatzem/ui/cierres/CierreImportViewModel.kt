package com.example.magatzem.ui.cierres

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.ArchivosPendientes
import com.example.magatzem.data.CierreImportadoEntity
import com.example.magatzem.data.CierreImportador
import com.example.magatzem.data.FormatoCierreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Importar cierres de caja de MiTPV (Ajustes → Importar, o directamente desde "Compartir" de MiTPV
 * en la misma tablet). La Activity guarda aquí el fichero recibido ([pendiente]) hasta que haya sesión
 * iniciada, y entonces se importa.
 */
class CierreImportViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MagatzemApplication
    private val importador = CierreImportador(app.database)

    val cierres: StateFlow<List<CierreImportadoEntity>> = app.database.cierreImportadoDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ficheros de MiTPV recibidos y sin procesar (carpeta `recibidos`); se avisa al abrir la app y se importan con un botón. */
    var pendientes by mutableStateOf<List<java.io.File>>(emptyList())
        private set

    /** Muestra el aviso "hay archivos sin procesar" (se oculta con "Más tarde" hasta la próxima vez que se abra la app). */
    var mostrarPendientes by mutableStateOf(false)
        private set

    fun refrescarPendientes() {
        pendientes = ArchivosPendientes.listar(app)
        mostrarPendientes = pendientes.isNotEmpty() && aviso == null && !trabajando
    }

    fun ocultarPendientes() {
        mostrarPendientes = false
    }

    var aviso by mutableStateOf<Pair<String, String>?>(null)
        private set

    var trabajando by mutableStateOf(false)
        private set

    /** Un fichero recibido por "Compartir": se guarda como pendiente y se ofrece importarlo (no se importa solo). */
    fun recibir(uri: Uri) {
        val nombre = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        ArchivosPendientes.guardar(app, uri, nombre)
        refrescarPendientes()
    }

    fun descartarAviso() {
        aviso = null
    }

    /** Importa el fichero elegido o recibido. Un cierre no pide confirmación: no borra nada, solo suma. */
    fun cargar(uri: Uri) {
        viewModelScope.launch {
            trabajando = true
            aviso = try {
                val texto = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } ?: throw FormatoCierreException("No se pudo abrir el fichero")
                val nombre = runCatching {
                    app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                }.getOrNull()
                val r = importador.importar(texto, nombre)
                val lineas = buildList {
                    add("Cierre del ${r.fechaCierre}: ${r.tickets} ticket(s), ${r.unidadesVendidas} unidad(es) vendidas" +
                        (if (r.unidadesDevueltas > 0) " y ${r.unidadesDevueltas} devuelta(s)" else "") +
                        ". Existencias actualizadas.")
                    add(buildString {
                        if (r.bancoNombre != null) append("Banco ${r.bancoNombre}: +%.2f € de tarjeta y +%.2f € ingresados desde caja. ".format(r.tarjetaABanco, r.ingresosABanco))
                        if (r.pagosPendientes.isNotEmpty()) append("Pendiente de registrar pago de factura: ${r.pagosPendientes.joinToString("; ")}. ")
                        if (r.pagosProveedor > 0) append("Pagos a proveedores desde caja: %.2f € (facturas marcadas como pagadas: ${r.facturasPagadas}). ".format(r.pagosProveedor))
                        append("Saldo de caja: %.2f €.".format(r.saldoCaja))
                    })
                    if (r.productosDesconocidos.isNotEmpty()) {
                        add("Artículos que ya no existen aquí (sin tocar existencias): ${r.productosDesconocidos.joinToString(", ")}.")
                    }
                    if (r.devolucionesSinOrigen.isNotEmpty()) {
                        add("Devoluciones sin ticket original en Magatzem (valoradas al coste actual): ${r.devolucionesSinOrigen.joinToString(", ")}.")
                    }
                    if (r.existenciasNegativas.isNotEmpty()) {
                        add("Existencia negativa tras las ventas: ${r.existenciasNegativas.joinToString(", ")}.")
                    }
                }
                "Cierre importado" to lineas.joinToString("\n\n")
            } catch (e: FormatoCierreException) {
                "No se puede importar" to (e.message ?: "Fichero no válido")
            } catch (e: Exception) {
                "No se ha importado nada" to "Ha fallado y no se ha cambiado ningún dato: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                trabajando = false
            }
        }
    }

    /**
     * Importa los ficheros pendientes por orden y borra cada uno cuando se ha importado. Uno que ya se importó antes también se borra (ya está
     * procesado); si un fichero falla, se para ahí y se conserva (y los siguientes quedan pendientes).
     */
    fun importarPendientes() {
        mostrarPendientes = false
        viewModelScope.launch {
            trabajando = true
            val resumen = mutableListOf<String>()
            var titulo = "Cierres importados"
            try {
                // Los cierres van por orden: uno que espera a su anterior se salta y se reintenta cuando se haya importado otro
                // (así da igual en qué orden hayan llegado los ficheros).
                val enEspera = linkedMapOf<String, String>()
                var importadosEnPasada: Int
                var pasadas = 0
                var parar = false
                do {
                    importadosEnPasada = 0
                    enEspera.clear()
                    for (archivo in ArchivosPendientes.listar(app)) {
                        try {
                            val texto = withContext(Dispatchers.IO) { archivo.readText(Charsets.UTF_8) }
                            val r = importador.importar(texto, archivo.name)
                            resumen += "Cierre del ${r.fechaCierre}: ${r.tickets} ticket(s), ${r.unidadesVendidas} unidad(es) vendidas" +
                                (if (r.unidadesDevueltas > 0) " y ${r.unidadesDevueltas} devuelta(s)" else "") + "."
                            if (r.devolucionesSinOrigen.isNotEmpty()) resumen += "Devoluciones sin ticket original (coste actual): ${r.devolucionesSinOrigen.joinToString(", ")}."
                            if (r.existenciasNegativas.isNotEmpty()) resumen += "Existencia negativa: ${r.existenciasNegativas.joinToString(", ")}."
                            if (r.pagosPendientes.isNotEmpty()) resumen += "PENDIENTE DE REGISTRAR PAGO DE FACTURA (pagada en efectivo desde MiTPV, la factura no está en Magatzem): ${r.pagosPendientes.joinToString("; ")}."
                            archivo.delete()
                            importadosEnPasada++
                        } catch (e: FormatoCierreException) {
                            val msg = e.message ?: ""
                            if (msg.contains("ya se import", ignoreCase = true)) {
                                resumen += "${archivo.name}: ya estaba importado, se descarta."
                                archivo.delete()
                            } else if (msg.startsWith("Falta importar antes")) {
                                enEspera[archivo.name] = msg
                            } else {
                                titulo = "Hay un fichero que no se puede importar"
                                resumen += "${archivo.name}: $msg"
                                parar = true
                                break
                            }
                        }
                    }
                } while (!parar && importadosEnPasada > 0 && enEspera.isNotEmpty() && ++pasadas < 20)
                if (enEspera.isNotEmpty()) {
                    titulo = "Hay un fichero que no se puede importar"
                    enEspera.forEach { (nombre, msg) -> resumen += "$nombre: $msg" }
                }
            } catch (e: Exception) {
                titulo = "No se ha importado todo"
                resumen += "Ha fallado y el fichero se conserva: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                trabajando = false
            }
            aviso = titulo to resumen.joinToString("\n\n").ifBlank { "No había nada que importar." }
            pendientes = ArchivosPendientes.listar(app)
        }
    }
}
