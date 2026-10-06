package com.example.magatzem.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.magatzem.ui.ajustes.DatosEmpresaScreen
import com.example.magatzem.ui.cierres.CierreImportDialogs
import com.example.magatzem.ui.cierres.CierresScreen
import com.example.magatzem.ui.cierres.rememberCierreImportViewModel
import com.example.magatzem.ui.ajustes.ExportarScreen
import com.example.magatzem.ui.ajustes.FormaPagoListadoScreen
import com.example.magatzem.ui.categorias.CategoriaAltaScreen
import com.example.magatzem.ui.categorias.CategoriaListadoScreen
import com.example.magatzem.ui.existencia.ExistenciaAltaScreen
import com.example.magatzem.ui.existencia.ExistenciaScreen
import com.example.magatzem.ui.inicio.InicioScreen
import com.example.magatzem.ui.incidencias.IncidenciasScreen
import com.example.magatzem.ui.oficina.BancoScreen
import com.example.magatzem.ui.oficina.BancosMovimientosScreen
import com.example.magatzem.ui.oficina.CajaScreen
import com.example.magatzem.ui.oficina.AlbaranEditarScreen
import com.example.magatzem.ui.oficina.FacturaEditarScreen
import com.example.magatzem.ui.oficina.OficinaAlbaranesScreen
import com.example.magatzem.ui.oficina.OficinaFacturasScreen
import com.example.magatzem.ui.oficina.OficinaPedidosScreen
import com.example.magatzem.ui.oficina.PagosSinFacturaScreen
import com.example.magatzem.ui.pedidos.PedidoFormScreen
import com.example.magatzem.ui.documentos.DocumentoScreen
import com.example.magatzem.ui.documentos.TipoDoc
import com.example.magatzem.ui.entradas.EntradaScreen
import com.example.magatzem.ui.proveedores.ProveedorAltaScreen
import com.example.magatzem.ui.proveedores.ProveedorListadoScreen
import com.example.magatzem.ui.sesion.CambiarPinDialog
import com.example.magatzem.ui.inicio.PantallaSinSesion
import com.example.magatzem.ui.sesion.rememberSesionViewModel
import com.example.magatzem.ui.usuarios.UsuarioAltaScreen
import com.example.magatzem.ui.usuarios.UsuarioListadoScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val sesion = rememberSesionViewModel()
    // Fichero de cierre recibido por "Compartir" desde MiTPV: se importa en cuanto hay sesión iniciada
    // (nunca antes del PIN) y se lleva al usuario a Ajustes → Importar.
    val cierreImport = rememberCierreImportViewModel()
    val recibido = cierreImport.pendiente
    LaunchedEffect(recibido, sesion.requierePin, sesion.usuarioPendienteCambioPin) {
        if (recibido != null && !sesion.requierePin && sesion.usuarioPendienteCambioPin == null) {
            cierreImport.cargar(recibido)
            sesion.enInicio = false
            navController.navigate(Routes.CIERRES) {
                popUpTo(Routes.INICIO) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    CierreImportDialogs(cierreImport)

    NavHost(navController = navController, startDestination = Routes.INICIO) {
        composable(Routes.INICIO) {
            AppScaffold(navController, currentRoute) { InicioScreen() }
        }
        composable(Routes.PROVEEDORES_ALTA) {
            AppScaffold(navController, currentRoute) {
                ProveedorAltaScreen(onCancelar = { navController.popBackStack() })
            }
        }
        composable(Routes.PROVEEDORES_LISTADO) {
            AppScaffold(navController, currentRoute) {
                ProveedorListadoScreen(onAnadir = { navController.navigate(Routes.PROVEEDORES_ALTA) })
            }
        }
        composable(Routes.CATEGORIAS_ALTA) {
            AppScaffold(navController, currentRoute) {
                CategoriaAltaScreen(onCancelar = { navController.popBackStack() })
            }
        }
        composable(Routes.CATEGORIAS_LISTADO) {
            AppScaffold(navController, currentRoute) {
                CategoriaListadoScreen(onAnadir = { navController.navigate(Routes.CATEGORIAS_ALTA) })
            }
        }
        composable(Routes.EXISTENCIA) {
            AppScaffold(navController, currentRoute) {
                ExistenciaScreen(onAnadir = { navController.navigate(Routes.EXISTENCIA_ALTA) })
            }
        }
        composable(Routes.EXISTENCIA_ALTA) {
            AppScaffold(navController, currentRoute) {
                ExistenciaAltaScreen(onCancelar = { navController.popBackStack() })
            }
        }
        composable(Routes.MAGATZEM_INICIO) {
            // Pantalla de entrada a Magatzem: vacía, solo con su menú.
            AppScaffold(navController, currentRoute) { }
        }
        composable(Routes.STOCK) {
            // Pantalla de Stock: sin contenido por ahora.
            AppScaffold(navController, currentRoute) { }
        }
        composable(Routes.OFICINA_INICIO) {
            // Pantalla de entrada a Oficina: vacía de momento, solo con su menú.
            AppScaffold(navController, currentRoute) { }
        }
        composable(Routes.OFICINA_PEDIDOS) {
            AppScaffold(navController, currentRoute) { OficinaPedidosScreen() }
        }
        composable(Routes.OFICINA_ALBARANES) {
            AppScaffold(navController, currentRoute) {
                OficinaAlbaranesScreen(onEditar = { id -> navController.navigate(Routes.albaranEditarRoute(id)) })
            }
        }
        composable(
            route = Routes.OFICINA_ALBARAN_EDITAR_PATTERN,
            arguments = listOf(navArgument("albaranId") { type = NavType.LongType })
        ) { entrada ->
            val albaranId = entrada.arguments?.getLong("albaranId") ?: -1L
            AppScaffold(navController, currentRoute) {
                AlbaranEditarScreen(albaranId = albaranId, onTerminado = { navController.popBackStack() })
            }
        }
        composable(Routes.OFICINA_FACTURAS) {
            AppScaffold(navController, currentRoute) {
                OficinaFacturasScreen(onEditar = { id -> navController.navigate(Routes.facturaEditarRoute(id)) })
            }
        }
        composable(
            route = Routes.OFICINA_FACTURA_EDITAR_PATTERN,
            arguments = listOf(navArgument("facturaId") { type = NavType.LongType })
        ) { entrada ->
            val facturaId = entrada.arguments?.getLong("facturaId") ?: -1L
            AppScaffold(navController, currentRoute) {
                FacturaEditarScreen(facturaId = facturaId, onTerminado = { navController.popBackStack() })
            }
        }
        composable(Routes.OFICINA_PAGOS_SIN_FACTURA) {
            AppScaffold(navController, currentRoute) { PagosSinFacturaScreen() }
        }
        composable(Routes.OFICINA_CAJA) {
            AppScaffold(navController, currentRoute) { CajaScreen() }
        }
        composable(Routes.OFICINA_BANCOS) {
            AppScaffold(navController, currentRoute) { BancosMovimientosScreen() }
        }
        composable(Routes.INCIDENCIAS) {
            AppScaffold(navController, currentRoute) { IncidenciasScreen() }
        }
        composable(Routes.AJUSTES_EXPORTAR) {
            AppScaffold(navController, currentRoute) { ExportarScreen() }
        }
        composable(Routes.CIERRES) {
            AppScaffold(navController, currentRoute) { CierresScreen() }
        }
        composable(Routes.ENTRADAS) {
            AppScaffold(navController, currentRoute) { EntradaScreen() }
        }
        composable(
            route = Routes.PEDIDOS_FORM_PATTERN,
            arguments = listOf(navArgument("pedidoId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val pedidoIdArg = backStackEntry.arguments?.getLong("pedidoId") ?: -1L
            AppScaffold(navController, currentRoute) {
                PedidoFormScreen(
                    pedidoId = pedidoIdArg.takeIf { it != -1L },
                    onGuardado = { navController.popBackStack() },
                    onVolver = { navController.popBackStack() },
                    onPasado = { tipo, id ->
                        // El pedido ya no existe: se abre el documento nuevo en su pantalla, listo para editar.
                        navController.navigate(if (tipo == TipoDoc.ALBARAN) Routes.albaranFormRoute(id) else Routes.facturaFormRoute(id)) {
                            popUpTo(Routes.INICIO) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
        composable(
            route = Routes.ALBARANES_FORM_PATTERN,
            arguments = listOf(navArgument("docId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("docId") ?: -1L
            AppScaffold(navController, currentRoute) {
                DocumentoScreen(
                    tipo = TipoDoc.ALBARAN, docId = id.takeIf { it != -1L },
                    onVolver = { navController.popBackStack() },
                    onAbrirFactura = { facturaId ->
                        navController.navigate(Routes.facturaFormRoute(facturaId)) {
                            popUpTo(Routes.INICIO) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
        composable(
            route = Routes.FACTURAS_FORM_PATTERN,
            arguments = listOf(navArgument("docId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("docId") ?: -1L
            AppScaffold(navController, currentRoute) {
                DocumentoScreen(
                    tipo = TipoDoc.FACTURA, docId = id.takeIf { it != -1L },
                    onVolver = { navController.popBackStack() },
                    onAbrirFactura = {}
                )
            }
        }
    }

    // Sin usuario identificado: pantalla de inicio con el logotipo (Magatzem, Oficina, Entrar y Recibir) que tapa la app.
    // Sin usuario dentro se vuelve siempre a ella; con usuario, se llega a ella desde el icono Inicio sin cerrar sesión.
    LaunchedEffect(sesion.requierePin) { if (sesion.requierePin) sesion.irAInicio() }
    if (sesion.enInicio && sesion.usuarioPendienteCambioPin == null) {
        PantallaSinSesion(sesion) { ruta ->
            sesion.enInicio = false
            navController.navigate(ruta) {
                popUpTo(Routes.INICIO) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    if (sesion.usuarioPendienteCambioPin != null) CambiarPinDialog(sesion)
}
