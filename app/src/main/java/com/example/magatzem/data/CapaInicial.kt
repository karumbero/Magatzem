package com.example.magatzem.data

import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Estado de la entrada que hace de capa inicial de coste (igual que en Almacén/MiTPV: «capa0»): siempre cuenta como la capa más antigua y no es una compra real. */
const val ESTADO_CAPA_INICIAL = "capa0"

/**
 * Capa inicial de coste: las unidades de un artículo que no tienen ninguna capa (existencia que se tecleó en su ficha, o anterior al control de capas) pasan a tener una, al coste
 * que tiene el artículo ahora. Es una entrada aparte («capa0») con una línea; no toca el artículo (ni existencia, ni coste, ni PVP, ni su fecha de cambio) y solo hace falta
 * si tiene coste: sin coste conocido no hay beneficio que calcular. Sin capa, las unidades salen primero y no cuentan coste.
 */
object CapaInicial {
    private fun hoy() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Crea la capa de las unidades de [productoId] que no tienen capa. Devuelve cuántas (0 si no hacía falta). Llamar dentro de una transacción si se hace junto a otros cambios. */
    suspend fun completar(db: AppDatabase, productoId: Long): Int {
        val p = db.productoDao().obtenerPorId(productoId) ?: return 0
        if (p.coste <= 0.0 || p.existencia <= 0) return 0
        val conCapa = db.consumoCapaDao().capasDisponibles(productoId).sumOf { maxOf(0, it.restante) }
        val sinCapa = p.existencia - conCapa
        if (sinCapa <= 0) return 0
        val exento = p.proveedorId?.let { id -> db.proveedorDao().obtenerTodos().firstOrNull { it.id == id }?.exentoIva } ?: false
        val total = Math.round(sinCapa * p.coste * factorCoste(exento) * 100.0) / 100.0
        val recepcionId = db.recepcionDao().insertarRecepcion(RecepcionEntity(proveedorId = p.proveedorId, fecha = hoy(), totalConIva = total, estadoFacturacion = ESTADO_CAPA_INICIAL))
        db.recepcionDao().insertarLineas(listOf(RecepcionLineaEntity(recepcionId = recepcionId, productoId = productoId, cantidad = sinCapa, coste = p.coste, margenBeneficio = p.margenBeneficio, subtotalConIva = total, costeLista = p.coste)))
        return sinCapa
    }

    /** Crea la capa inicial de todos los artículos con existencia y coste que no la tengan. Solo añade entradas: no cambia ningún dato existente. Devuelve (artículos, unidades). */
    suspend fun regularizar(db: AppDatabase): Pair<Int, Int> = db.withTransaction {
        var articulos = 0; var unidades = 0
        db.productoDao().obtenerTodosOrdenados().filter { it.existencia > 0 && it.coste > 0.0 }.forEach { p ->
            val n = completar(db, p.id)
            if (n > 0) { articulos++; unidades += n }
        }
        articulos to unidades
    }
}
