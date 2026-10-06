package com.example.magatzem.ui.nav

object Routes {
    const val INICIO = "inicio"
    const val PROVEEDORES_ALTA = "proveedores_alta"
    const val PROVEEDORES_LISTADO = "proveedores_listado"
    const val CATEGORIAS_ALTA = "categorias_alta"
    const val CATEGORIAS_LISTADO = "categorias_listado"
    /** Alta/listado de artículos ya existentes marcados como Pre (antes "Existentes", renombrado 2026-09-30). */
    const val EXISTENCIA = "existencia"
    const val EXISTENCIA_ALTA = "existencia_alta"
    const val AJUSTES_EXPORTAR = "ajustes_exportar"
    const val ENTRADAS = "entradas"
    /** Oficina → Importar: cierres de caja de MiTPV (la ruta conserva su nombre original). */
    const val CIERRES = "cierres"
    const val INCIDENCIAS = "incidencias"
    const val REVISION = "revision_datos"
    const val MAGATZEM_INICIO = "magatzem_inicio"
    const val STOCK = "stock"
    const val OFICINA_INICIO = "oficina_inicio"
    const val OFICINA_PEDIDOS = "oficina_pedidos"
    const val OFICINA_ALBARANES = "oficina_albaranes"
    /** Oficina → Albaranes → un albarán abierto para revisar y modificar (sus cambios se aplican a los artículos). */
    const val OFICINA_ALBARAN_EDITAR_PATTERN = "oficina_albaran/{albaranId}"
    fun albaranEditarRoute(albaranId: Long) = "oficina_albaran/$albaranId"
    const val OFICINA_FACTURA_EDITAR_PATTERN = "oficina_factura/{facturaId}"
    fun facturaEditarRoute(facturaId: Long) = "oficina_factura/$facturaId"
    const val OFICINA_FACTURAS = "oficina_facturas"
    const val OFICINA_CAJA = "oficina_caja"
    const val OFICINA_BANCOS = "oficina_bancos"
    const val OFICINA_PAGOS_SIN_FACTURA = "oficina_pagos_sin_factura"
    /** Patrón de registro en el NavHost; `pedidoId=-1` (ver [PEDIDOS_NUEVO]) significa "pedido nuevo". */
    const val PEDIDOS_FORM_PATTERN = "pedidos_form?pedidoId={pedidoId}"
    const val PEDIDOS_NUEVO = "pedidos_form?pedidoId=-1"

    /** Albarán y Factura de Movimientos; `docId=-1` = lista/nuevo, otro valor abre ese documento. */
    const val ALBARANES_FORM_PATTERN = "albaranes_form?docId={docId}"
    const val ALBARANES_NUEVO = "albaranes_form?docId=-1"
    fun albaranFormRoute(docId: Long) = "albaranes_form?docId=$docId"
    const val FACTURAS_FORM_PATTERN = "facturas_form?docId={docId}"
    const val FACTURAS_NUEVO = "facturas_form?docId=-1"
    fun facturaFormRoute(docId: Long) = "facturas_form?docId=$docId"
    fun pedidosEditarRoute(pedidoId: Long) = "pedidos_form?pedidoId=$pedidoId"
}
