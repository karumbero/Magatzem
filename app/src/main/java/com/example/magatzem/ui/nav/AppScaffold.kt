package com.example.magatzem.ui.nav

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.magatzem.ui.sesion.rememberSesionViewModel

private val BarBackground = Color(0xFF0D47A1)
// Oficina lleva en la barra el color de su botón de inicio (turquesa).
private val BarOficina = Color(0xFF1596AD)
private val BarActiveHighlight = Color(0x33FFFFFF)

private data class MenuSubItem(val label: String, val route: String)

private data class MenuSectionSpec(
    val label: String,
    val items: List<MenuSubItem>,
    val extraHighlightRoutes: List<String> = emptyList()
) {
    val routes: List<String> get() = items.map { it.route } + extraHighlightRoutes
}

// Izquierda: Artículos (con categorías y proveedores), Movimientos y Stock (vacío por ahora).
private val SECCIONES = listOf(
    MenuSectionSpec(
        "Artículos",
        listOf(
            MenuSubItem("Artículos", Routes.EXISTENCIA),
            MenuSubItem("Categorías", Routes.CATEGORIAS_LISTADO),
            MenuSubItem("Proveedores", Routes.PROVEEDORES_LISTADO)
        ),
        extraHighlightRoutes = listOf(Routes.EXISTENCIA_ALTA)
    ),
    MenuSectionSpec(
        "Movimientos",
        listOf(
            MenuSubItem("Pedido", Routes.PEDIDOS_NUEVO),
            MenuSubItem("Albarán", Routes.ALBARANES_NUEVO),
            MenuSubItem("Factura", Routes.FACTURAS_NUEVO),
            MenuSubItem("Incidencia", Routes.INCIDENCIAS)
        ),
        extraHighlightRoutes = listOf(Routes.PEDIDOS_FORM_PATTERN, Routes.ALBARANES_FORM_PATTERN, Routes.FACTURAS_FORM_PATTERN)
    ),
    MenuSectionSpec("Stock", listOf(MenuSubItem("Stock", Routes.STOCK)))
)

/** Oficina va como icono de edificio a la derecha, solo para nivel 1. */
private val OFICINA_SECCION = MenuSectionSpec(
    "Oficina",
    listOf(
        MenuSubItem("Facturas", Routes.OFICINA_FACTURAS),
        MenuSubItem("Albaranes", Routes.OFICINA_ALBARANES),
        MenuSubItem("Pedidos", Routes.OFICINA_PEDIDOS),
        MenuSubItem("Pagos", Routes.OFICINA_PAGOS_SIN_FACTURA),
        MenuSubItem("Caja", Routes.OFICINA_CAJA),
        MenuSubItem("Bancos", Routes.OFICINA_BANCOS)
    ),
    extraHighlightRoutes = listOf(Routes.OFICINA_ALBARAN_EDITAR_PATTERN, Routes.OFICINA_FACTURA_EDITAR_PATTERN, Routes.OFICINA_INICIO)
)

/** Ajustes va como icono de engranaje a la derecha, solo para nivel 1. */
private val AJUSTES_SECCION = MenuSectionSpec(
    "Ajustes",
    listOf(
        MenuSubItem("Exportar", Routes.AJUSTES_EXPORTAR),
        MenuSubItem("Importar", Routes.CIERRES)
    )
)

@Composable
fun AppScaffold(
    navController: NavHostController,
    currentRoute: String?,
    content: @Composable () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopMenuBar(navController = navController, currentRoute = currentRoute) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            content()
        }
    }
}

@Composable
private fun TopMenuBar(navController: NavHostController, currentRoute: String?) {
    val sesion = rememberSesionViewModel()
    val activity = LocalActivity.current

    fun navegar(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.INICIO) { inclusive = false }
            launchSingleTop = true
        }
    }

    val enOficina = currentRoute in OFICINA_SECCION.routes
    Surface(color = if (enOficina) BarOficina else BarBackground) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vuelve a la pantalla de inicio (logotipo) sin cerrar la sesión.
            MenuIconButton(
                icon = Icons.Filled.Home,
                contentDescription = "Inicio",
                activa = false,
                onClick = { sesion.irAInicio() }
            )
            if (enOficina) {
                // En Oficina el menú son sus propias opciones, una a una (no las de Magatzem).
                OFICINA_SECCION.items.forEach { item ->
                    MenuSectionButton(
                        seccion = MenuSectionSpec(item.label, listOf(item)),
                        activa = currentRoute == item.route ||
                            (item.route == Routes.OFICINA_ALBARANES && currentRoute == Routes.OFICINA_ALBARAN_EDITAR_PATTERN) ||
                            (item.route == Routes.OFICINA_FACTURAS && currentRoute == Routes.OFICINA_FACTURA_EDITAR_PATTERN),
                        onNavigate = ::navegar
                    )
                }
            } else {
                SECCIONES.forEach { seccion ->
                    MenuSectionButton(
                        seccion = seccion,
                        activa = currentRoute in seccion.routes,
                        onNavigate = ::navegar
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // Oficina y Ajustes (engranaje) son solo para nivel 1.
            if (sesion.puedeGestionarUsuarios) {
                // Revisión de datos: artículos con campos sin rellenar o incoherentes.
                MenuIconButton(
                    icon = Icons.Filled.BugReport,
                    contentDescription = "Revisión de datos",
                    activa = currentRoute == Routes.REVISION,
                    onClick = { navegar(Routes.REVISION) }
                )
                MenuIconSectionButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Ajustes",
                    seccion = AJUSTES_SECCION,
                    activa = currentRoute in AJUSTES_SECCION.routes,
                    onNavigate = ::navegar
                )
            }
            MenuIconButton(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = "Salir",
                activa = false,
                onClick = {
                    // Cierra la sesión (la próxima vez que se abra la app pedirá login otra vez)
                    // y cierra la app entera, en vez de dejarla abierta mostrando el login.
                    sesion.cerrarSesion()
                    activity?.finishAffinity()
                }
            )
        }
    }
}

/** Como [MenuIconButton] pero con desplegable (como [MenuSectionButton] con icono en vez de texto). */
@Composable
private fun MenuIconSectionButton(
    icon: ImageVector,
    contentDescription: String,
    seccion: MenuSectionSpec,
    activa: Boolean,
    onNavigate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .background(
                color = if (activa) BarActiveHighlight else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            seccion.items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        expanded = false
                        onNavigate(item.route)
                    }
                )
            }
        }
    }
}

@Composable
private fun MenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    activa: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .background(
                color = if (activa) BarActiveHighlight else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White)
        }
    }
}

@Composable
private fun MenuSectionButton(
    seccion: MenuSectionSpec,
    activa: Boolean,
    onNavigate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val soloUnItem = seccion.items.size == 1
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .background(
                color = if (activa) BarActiveHighlight else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        TextButton(
            onClick = {
                if (soloUnItem) {
                    onNavigate(seccion.items.first().route)
                } else {
                    expanded = true
                }
            },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) {
            Text(
                text = seccion.label,
                fontWeight = if (activa) FontWeight.Bold else FontWeight.Normal,
                color = Color.White
            )
        }
        if (!soloUnItem) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                seccion.items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = {
                            expanded = false
                            onNavigate(item.route)
                        }
                    )
                }
            }
        }
    }
}
