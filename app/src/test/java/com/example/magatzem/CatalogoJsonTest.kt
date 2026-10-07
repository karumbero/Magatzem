package com.example.magatzem

import com.example.magatzem.data.CatalogoMagatzemJson
import com.example.magatzem.data.CategoriaEntity
import com.example.magatzem.data.DatosEmpresaEntity
import com.example.magatzem.data.ProductoEntity
import com.example.magatzem.data.ProveedorEntity
import com.example.magatzem.data.UsuarioEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoJsonTest {
    private fun json(primera: Boolean, caja: Double?) = CatalogoMagatzemJson.construir(
        primeraExportacion = primera,
        usuarios = if (primera) listOf(UsuarioEntity(nombre = "Mar", pin = "151276", nivel = 1, uuid = "u1")) else null,
        datosEmpresa = if (primera) DatosEmpresaEntity(nombre = "Sa Platgeta") else null,
        formasPago = if (primera) emptyList() else null,
        proveedores = listOf(ProveedorEntity(id = 1, nombre = "Prov", uuid = "pv1")),
        categorias = listOf(CategoriaEntity(id = 1, nombre = "Cat", uuid = "ct1")),
        articulos = listOf(ProductoEntity(id = 1, sku = "1/A", nombre = "Art", categoriaId = 1, proveedorId = 1, coste = 1.0, precioVenta = 3.0, uuid = "a1", fechaCreacion = "x", fechaActualizacion = "x")),
        articulosVigentes = listOf("a1"),
        categoriaUuidPorId = mapOf(1L to "ct1"),
        proveedorUuidPorId = mapOf(1L to "pv1"),
        origen = "dev",
        exportadoEn = "2026-10-07 12:00:00",
        cajaInicial = caja
    )

    @Test
    fun laPrimeraExportacionLlevaLaCajaInicial() {
        val j = json(true, 1000.0)
        assertTrue(j.contains("\"cajaInicial\": 1000.00"))
        java.io.File("/tmp/catalogo_con_caja.json").writeText(j)
        java.io.File("/tmp/catalogo_sin_caja.json").writeText(json(true, null))
    }

    @Test
    fun sinCajaInicialVaNulo() {
        assertTrue(json(false, null).contains("\"cajaInicial\": null"))
    }
}
