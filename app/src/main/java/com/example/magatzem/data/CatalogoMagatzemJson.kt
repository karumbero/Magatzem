package com.example.magatzem.data

/**
 * Exportación al programa de ventas. Magatzem manda el catálogo; el programa de ventas solo lee
 * (ver `magatzem-project` en memoria, mismo reparto que BackShop↔MiTPV). Nunca se manda existencia:
 * el programa de ventas no gestiona stock, solo vende con lo que aquí se le diga que hay y a qué
 * precio.
 *
 * `primeraExportacion=true` (tabla `exportaciones_catalogo` vacía hasta ahora, es decir, la apertura)
 * manda usuarios, datos de empresa y formas de pago (incluye los bancos, con IBAN/titular), además de
 * TODOS los artículos (sin existencias). En cualquier exportación siguiente esos tres van a `null`
 * (ya los tiene) y `articulos` es solo lo nuevo o modificado desde la última vez. `proveedores` y
 * `categorias`, en cambio, van completos en TODAS las exportaciones (se crean/actualizan por uuid en
 * destino, sin borrar), para que un artículo nuevo nunca llegue con una categoría o proveedor que
 * el programa de ventas no conoce. `articulosVigentes` (uuid de todos los artículos que existen) y las
 * listas completas de proveedores y categorías permiten además aplicar los **borrados**: lo que el
 * programa de ventas tenga y no esté en esas listas se borra allí. Las entradas (recepciones, albaranes, facturas) no se mandan: la
 * primera exportación las borra de Magatzem (ver [CatalogoExportador]).
 */
object CatalogoMagatzemJson {
    const val FORMATO = "magatzem-catalogo"
    const val VERSION = 1

    fun construir(
        primeraExportacion: Boolean,
        usuarios: List<UsuarioEntity>?,
        datosEmpresa: DatosEmpresaEntity?,
        formasPago: List<FormaPagoEntity>?,
        proveedores: List<ProveedorEntity>?,
        categorias: List<CategoriaEntity>?,
        articulos: List<ProductoEntity>,
        articulosVigentes: List<String>,
        categoriaUuidPorId: Map<Long, String>,
        proveedorUuidPorId: Map<Long, String>,
        origen: String,
        exportadoEn: String
    ): String = buildString {
        append("{\n")
        append("  \"formato\": ").append(texto(FORMATO)).append(",\n")
        append("  \"version\": ").append(VERSION).append(",\n")
        append("  \"exportadoEn\": ").append(texto(exportadoEn)).append(",\n")
        append("  \"origen\": ").append(texto(origen)).append(",\n")
        append("  \"primeraExportacion\": ").append(primeraExportacion).append(",\n")
        append("  \"usuarios\": ").append(
            usuarios?.let {
                lista(it) { u ->
                    objeto(
                        "uuid" to Campo.Texto(u.uuid),
                        "nombre" to Campo.Texto(u.nombre),
                        "pin" to Campo.Texto(u.pin),
                        "nivel" to Campo.Numero(u.nivel)
                    )
                }
            } ?: "null"
        ).append(",\n")
        append("  \"datosEmpresa\": ").append(
            datosEmpresa?.let {
                objeto(
                    "nombre" to Campo.Texto(it.nombre),
                    "nombreComercial" to Campo.TextoNulo(it.nombreComercial),
                    "nifCif" to Campo.TextoNulo(it.nifCif),
                    "direccion" to Campo.TextoNulo(it.direccion),
                    "codigoPostal" to Campo.TextoNulo(it.codigoPostal),
                    "ciudad" to Campo.TextoNulo(it.ciudad),
                    "telefono" to Campo.TextoNulo(it.telefono),
                    "email" to Campo.TextoNulo(it.email)
                )
            } ?: "null"
        ).append(",\n")
        append("  \"formasPago\": ").append(
            formasPago?.let {
                lista(it) { f ->
                    objeto(
                        "uuid" to Campo.Texto(f.uuid),
                        "nombre" to Campo.Texto(f.nombre),
                        "activo" to Campo.Numero(if (f.activo) 1 else 0),
                        "iban" to Campo.TextoNulo(f.iban),
                        "titular" to Campo.TextoNulo(f.titular)
                    )
                }
            } ?: "null"
        ).append(",\n")
        append("  \"proveedores\": ").append(
            proveedores?.let {
                lista(it) { p ->
                    objeto(
                        "uuid" to Campo.Texto(p.uuid),
                        "nombre" to Campo.Texto(p.nombre),
                        "comercial" to Campo.TextoNulo(p.comercial),
                        "telefono" to Campo.TextoNulo(p.telefono),
                        "email" to Campo.TextoNulo(p.email),
                        "nifCif" to Campo.TextoNulo(p.nifCif),
                        "direccion" to Campo.TextoNulo(p.direccion),
                        "codigoPostal" to Campo.TextoNulo(p.codigoPostal),
                        "ciudad" to Campo.TextoNulo(p.ciudad),
                        "exentoIva" to Campo.Booleano(p.exentoIva)
                    )
                }
            } ?: "null"
        ).append(",\n")
        append("  \"categorias\": ").append(
            categorias?.let {
                lista(it) { c ->
                    objeto(
                        "uuid" to Campo.Texto(c.uuid),
                        "nombre" to Campo.Texto(c.nombre),
                        "parentUuid" to Campo.TextoNulo(c.parentId?.let { id -> categoriaUuidPorId[id] })
                    )
                }
            } ?: "null"
        ).append(",\n")
        // Todos los artículos que existen ahora (solo su uuid): el programa de ventas borra los que no
        // estén en esta lista, así los borrados también viajan aunque `articulos` solo lleve los cambios.
        append("  \"articulosVigentes\": ").append(
            articulosVigentes.joinToString(prefix = "[", postfix = "]", separator = ",") { texto(it) }
        ).append(",\n")
        // Artículos: identificación y precio, nunca existencia (ver doc de arriba). categoriaId/
        // proveedorId son locales de este aparato: se manda su uuid, no el id.
        append("  \"articulos\": ").append(
            lista(articulos) { a ->
                objeto(
                    "uuid" to Campo.Texto(a.uuid),
                    "sku" to Campo.TextoNulo(a.sku),
                    "codigoBarras" to Campo.TextoNulo(a.codigoBarras),
                    "referenciaFabricante" to Campo.TextoNulo(a.referenciaFabricante),
                    "nombre" to Campo.Texto(a.nombre),
                    "categoriaUuid" to Campo.TextoNulo(a.categoriaId?.let { categoriaUuidPorId[it] }),
                    "proveedorUuid" to Campo.TextoNulo(a.proveedorId?.let { proveedorUuidPorId[it] }),
                    "tipo" to Campo.Texto(a.tipo),
                    "coste" to Campo.Decimal(a.coste),
                    "margenBeneficio" to Campo.Decimal(a.margenBeneficio),
                    "precioVenta" to Campo.Decimal(a.precioVenta),
                    "mostrarEnTeclado" to Campo.Booleano(a.mostrarEnTeclado)
                )
            }
        ).append("\n")
        append("}\n")
    }

    private sealed class Campo {
        data class Texto(val valor: String) : Campo()
        data class TextoNulo(val valor: String?) : Campo()
        data class Numero(val valor: Int) : Campo()
        data class Decimal(val valor: Double) : Campo()
        data class Booleano(val valor: Boolean) : Campo()
    }

    private fun <T> lista(items: List<T>, aObjeto: (T) -> String): String =
        if (items.isEmpty()) "[]"
        else items.joinToString(prefix = "[\n", separator = ",\n", postfix = "\n  ]") { "    " + aObjeto(it) }

    private fun objeto(vararg campos: Pair<String, Campo>): String =
        campos.joinToString(prefix = "{", separator = ", ", postfix = "}") { (clave, valor) ->
            val valorJson = when (valor) {
                is Campo.Numero -> valor.valor.toString()
                is Campo.Decimal -> valor.valor.toString()
                is Campo.Booleano -> valor.valor.toString()
                is Campo.Texto -> texto(valor.valor)
                is Campo.TextoNulo -> valor.valor?.let { texto(it) } ?: "null"
            }
            texto(clave) + ": " + valorJson
        }

    /** Cadena JSON entre comillas, escapando comillas, barras y caracteres de control. */
    private fun texto(valor: String): String = buildString {
        append('"')
        for (c in valor) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
