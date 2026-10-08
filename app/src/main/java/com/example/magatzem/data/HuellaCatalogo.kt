package com.example.magatzem.data

import java.security.MessageDigest

/**
 * Huella de "lo que el programa de ventas tiene que saber además de los artículos modificados": las
 * categorías (con su padre), los proveedores y qué artículos existen. Si cambia entre dos
 * exportaciones (renombrar, crear o **borrar** una categoría, un proveedor o un artículo), hay algo
 * que exportar aunque ningún artículo haya cambiado. Se guarda en cada exportación
 * ([ExportacionCatalogoEntity.huellaMaestros]) para comparar con la siguiente.
 */
object HuellaCatalogo {
    fun calcular(
        categorias: List<CategoriaEntity>,
        proveedores: List<ProveedorEntity>,
        uuidsArticulos: List<String>,
        /** Los usuarios de nivel 1: son los únicos que MiTPV recibe de Magatzem (el resto de usuarios de MiTPV son suyos). */
        usuariosNivel1: List<UsuarioEntity> = emptyList()
    ): String {
        val uuidPorId = categorias.associate { it.id to it.uuid }
        val texto = buildString {
            categorias.map { "C|${it.uuid}|${it.nombre}|${it.parentId?.let { id -> uuidPorId[id] }.orEmpty()}" }
                .sorted().forEach { appendLine(it) }
            proveedores.map {
                "P|${it.uuid}|${it.nombre}|${it.comercial}|${it.telefono}|${it.email}|${it.nifCif}|" +
                    "${it.direccion}|${it.codigoPostal}|${it.ciudad}" + (if (it.exentoIva) "|EXENTO" else "")
            }.sorted().forEach { appendLine(it) }
            uuidsArticulos.sorted().forEach { appendLine("A|$it") }
            // Solo si hay usuarios de nivel 1 (así la huella no cambia mientras no los haya).
            usuariosNivel1.filter { it.nivel == 1 }.map { "U|${it.uuid}|${it.nombre}|${it.pin}|${it.nivel}|${it.activo}" }.sorted().forEach { appendLine(it) }
        }
        return MessageDigest.getInstance("SHA-256").digest(texto.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
