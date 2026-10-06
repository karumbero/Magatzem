package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Dos niveles nada más: una categoría con `parentId` nulo es de primer nivel; una con `parentId` no
 * nulo es su subcategoría. No se permiten subcategorías de subcategorías (ver comprobación en
 * `CategoriaViewModel.actualizar`): el selector de categoría padre solo ofrece categorías de primer
 * nivel. Borrar el padre no borra las hijas: se quedan sueltas (`SET_NULL`), aunque en la práctica
 * la app ya bloquea el borrado de una categoría mientras tenga subcategorías (igual que ya bloqueaba
 * el borrado mientras tuviera artículos).
 */
@Entity(
    tableName = "categorias",
    foreignKeys = [
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = SET_NULL
        )
    ],
    indices = [
        Index(value = ["nombre"], unique = true),
        Index(value = ["uuid"], unique = true),
        Index(value = ["parentId"])
    ]
)
data class CategoriaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombre: String,
    /** Identidad estable entre aparatos (los `id` autonuméricos son locales). */
    val uuid: String = UUID.randomUUID().toString(),
    /** Null = categoría de primer nivel. No nulo = subcategoría de la categoría con ese `id`. */
    val parentId: Long? = null
)

/**
 * Para listados/selectores: categorías de primer nivel (alfabético) seguidas cada una de sus
 * subcategorías (también alfabético), listas para pintar en plano con indentación/prefijo. El
 * `Boolean` indica si es subcategoría.
 */
fun ordenarConJerarquia(categorias: List<CategoriaEntity>): List<Pair<CategoriaEntity, Boolean>> {
    val padres = categorias.filter { it.parentId == null }.sortedBy { it.nombre.lowercase() }
    val hijasPorPadre = categorias.filter { it.parentId != null }.groupBy { it.parentId }
    return padres.flatMap { padre ->
        val hijas = hijasPorPadre[padre.id]?.sortedBy { it.nombre.lowercase() } ?: emptyList()
        listOf(padre to false) + hijas.map { it to true }
    }
}
