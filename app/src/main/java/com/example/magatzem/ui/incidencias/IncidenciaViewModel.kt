package com.example.magatzem.ui.incidencias

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.magatzem.MagatzemApplication
import com.example.magatzem.data.CapasIncidencia
import com.example.magatzem.data.IncidenciaEntity
import com.example.magatzem.data.ProductoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun ahora(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

/**
 * Incidencias (Movimientos → Incidencias): artículos de baja (dañados o perdidos).
 * - Añadir: guarda la incidencia y resta las unidades de la existencia.
 * - Editar: tipo, unidades y nota; si cambian las unidades, se ajusta la diferencia.
 * - Recuperar: devuelve las unidades a la existencia y borra la incidencia.
 * Todo en una transacción, para que existencia e incidencia no queden nunca a medias.
 */
class IncidenciaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MagatzemApplication).database
    private val dao = db.incidenciaDao()
    private val productoDao = db.productoDao()

    val incidencias: StateFlow<List<IncidenciaEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productos: StateFlow<List<ProductoEntity>> = productoDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun anadir(producto: ProductoEntity, tipo: String, cantidad: Int, nota: String) {
        viewModelScope.launch {
            db.withTransaction {
                val fecha = ahora()
                val id = dao.insertar(
                    IncidenciaEntity(
                        productoId = producto.id,
                        productoNombre = producto.nombre,
                        productoSku = producto.sku,
                        tipo = tipo,
                        cantidad = cantidad,
                        costeUnitario = producto.coste,
                        fecha = fecha,
                        nota = nota.trim().ifBlank { null }
                    )
                )
                // Las unidades salen de las capas más antiguas y la incidencia se queda con su coste real.
                val coste = CapasIncidencia.gastar(db, producto.id, cantidad, id, fecha, producto.coste)
                dao.actualizar(dao.obtenerPorId(id)!!.copy(costeUnitario = coste))
                productoDao.sumarExistencia(producto.id, -cantidad)
            }
        }
    }

    fun editar(incidencia: IncidenciaEntity, tipo: String, cantidad: Int, nota: String) {
        viewModelScope.launch {
            db.withTransaction {
                val diferencia = cantidad - incidencia.cantidad
                var coste = incidencia.costeUnitario
                incidencia.productoId?.let { productoId ->
                    if (diferencia != 0) {
                        productoDao.sumarExistencia(productoId, -diferencia)
                        // Se devuelven las unidades anteriores a sus capas y se gastan de nuevo las nuevas (FIFO).
                        CapasIncidencia.liberar(db, incidencia.id)
                        coste = CapasIncidencia.gastar(
                            db, productoId, cantidad, incidencia.id, incidencia.fecha,
                            productoDao.obtenerPorId(productoId)?.coste ?: incidencia.costeUnitario
                        )
                    }
                }
                dao.actualizar(incidencia.copy(tipo = tipo, cantidad = cantidad, nota = nota.trim().ifBlank { null }, costeUnitario = coste))
            }
        }
    }

    fun recuperar(incidencia: IncidenciaEntity) {
        viewModelScope.launch {
            db.withTransaction {
                incidencia.productoId?.let { productoDao.sumarExistencia(it, incidencia.cantidad) }
                // Las unidades vuelven a las capas de las que salieron.
                CapasIncidencia.liberar(db, incidencia.id)
                dao.eliminar(incidencia)
            }
        }
    }
}
