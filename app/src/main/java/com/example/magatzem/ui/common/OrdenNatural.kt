package com.example.magatzem.ui.common

import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Orden "natural" de textos: los números se comparan por su valor, no letra a letra, y las mayúsculas
 * no cuentan. Así "Abanico 2" va antes que "Abanico 10" y "4.50" antes que "5" (con el orden
 * alfabético normal de SQLite iría "10" antes que "2").
 */
object OrdenNatural : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                // Número con parte decimal opcional ("4", "4.50", "4,5"): se compara por valor.
                var fi = i
                while (fi < a.length && a[fi].isDigit()) fi++
                var fj = j
                while (fj < b.length && b[fj].isDigit()) fj++
                val ea = a.substring(i, fi).trimStart('0')
                val eb = b.substring(j, fj).trimStart('0')
                if (ea.length != eb.length) return ea.length - eb.length
                val c = ea.compareTo(eb)
                if (c != 0) return c
                var di = fi
                if (di + 1 < a.length && (a[di] == '.' || a[di] == ',') && a[di + 1].isDigit()) { di++; while (di < a.length && a[di].isDigit()) di++ }
                var dj = fj
                if (dj + 1 < b.length && (b[dj] == '.' || b[dj] == ',') && b[dj + 1].isDigit()) { dj++; while (dj < b.length && b[dj].isDigit()) dj++ }
                val fa = if (di > fi) a.substring(fi + 1, di).trimEnd('0') else ""
                val fb = if (dj > fj) b.substring(fj + 1, dj).trimEnd('0') else ""
                val cf = fa.compareTo(fb)
                if (cf != 0) return cf
                i = di
                j = dj
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}


@JvmName("ordenNaturalProductos")
fun Flow<List<ProductoEntity>>.ordenNatural(): Flow<List<ProductoEntity>> =
    map { l -> l.sortedWith(compareBy(OrdenNatural) { it.nombre }) }

@JvmName("ordenNaturalProveedores")
fun Flow<List<ProveedorEntity>>.ordenNatural(): Flow<List<ProveedorEntity>> =
    map { l -> l.sortedWith(compareBy(OrdenNatural) { it.nombre }) }

@JvmName("ordenNaturalCategorias")
fun Flow<List<CategoriaEntity>>.ordenNatural(): Flow<List<CategoriaEntity>> =
    map { l -> l.sortedWith(compareBy(OrdenNatural) { it.nombre }) }
