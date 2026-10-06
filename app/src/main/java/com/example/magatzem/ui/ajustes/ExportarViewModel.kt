package com.example.magatzem.ui.ajustes

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.DispositivoId
import com.example.magatzem.data.ExportacionCatalogoEntity
import com.example.magatzem.data.HuellaCatalogo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ExportarViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val catalogoExportador = (application as MagatzemApplication).catalogoExportador

    val dispositivoId: String = DispositivoId.obtener(application)

    val ultimaExportacion: StateFlow<ExportacionCatalogoEntity?> = db.exportacionCatalogoDao().observeUltima()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Primera vez (todavía no hay ninguna exportación): cuenta todos los artículos, se mandan todos. */
    val pendientes: StateFlow<Int> = db.exportacionCatalogoDao().observeUltima()
        .flatMapLatest { ultima ->
            if (ultima == null) db.productoDao().observeCantidadTotal()
            else db.productoDao().observeCantidadPendientesDesde(ultima.fecha)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * true si, desde la última exportación, ha cambiado (o se ha borrado) algo de categorías,
     * proveedores o artículos existentes aunque ningún artículo esté "pendiente" (ver [HuellaCatalogo]).
     * Antes de la primera exportación no aplica: se manda todo.
     */
    val cambiosEnMaestros: StateFlow<Boolean> = combine(
        db.exportacionCatalogoDao().observeUltima(),
        db.categoriaDao().observeAll(),
        db.proveedorDao().observeAll(),
        db.productoDao().observeUuids()
    ) { ultima, categorias, proveedores, uuids ->
        ultima != null && ultima.huellaMaestros != HuellaCatalogo.calcular(categorias, proveedores, uuids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Entradas que borrará la primera exportación: recepciones, albaranes y facturas. */
    val entradasABorrar: StateFlow<Triple<Int, Int, Int>> = combine(
        db.recepcionDao().observeCantidadRecepciones(),
        db.recepcionDao().observeCantidadAlbaranes(),
        db.recepcionDao().observeCantidadFacturas()
    ) { recepciones, albaranes, facturas -> Triple(recepciones, albaranes, facturas) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0, 0, 0))

    /** Facturas aún sin pagar del todo: la primera exportación las borraría con el resto (avisarlo). */
    val facturasPendientes: StateFlow<Int> = db.recepcionDao().observeCantidadFacturasPendientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Fichero de la última exportación, para reenviarlo (p. ej. si se eligió la app equivocada al compartir); null si ya no está. */
    fun archivoUltimaExportacion(): File? {
        val nombre = ultimaExportacion.value?.nombreArchivo ?: return null
        return File(catalogoExportador.carpeta(), nombre).takeIf { it.exists() }
    }

    var exportando by mutableStateOf(false)
        private set

    fun exportar(onExito: (File) -> Unit, onNada: () -> Unit) {
        if (exportando) return
        exportando = true
        viewModelScope.launch {
            val archivo = catalogoExportador.exportar()
            exportando = false
            if (archivo != null) onExito(archivo) else onNada()
        }
    }
}
