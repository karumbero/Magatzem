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

    /** Fichero recibido por "Compartir" que aún no se ha importado (a la espera de la sesión). */
    var pendiente by mutableStateOf<Uri?>(null)
        private set

    var aviso by mutableStateOf<Pair<String, String>?>(null)
        private set

    var trabajando by mutableStateOf(false)
        private set

    fun recibir(uri: Uri) {
        pendiente = uri
    }

    fun descartarAviso() {
        aviso = null
    }

    /** Importa el fichero elegido o recibido. Un cierre no pide confirmación: no borra nada, solo suma. */
    fun cargar(uri: Uri) {
        pendiente = null
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
}
