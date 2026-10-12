package com.example.magatzem.ui.sol

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pantalla oculta a la que se llega pulsando el sol del logotipo de la pantalla de inicio: en blanco, con la barra de menú y el
 * botón Salir a la derecha, que vuelve a la pantalla de inicio (sin cerrar la app ni la sesión). Menú: Retirar y Retirado.
 */
@Composable
fun PantallaSol(onSalir: () -> Unit) {
    // 0 = en blanco, 1 = Retirar, 2 = Retirado.
    var seccion by rememberSaveable { mutableIntStateOf(0) }
    // Un solo ViewModel para las dos secciones (la retirada en curso no se pierde al mirar Retirado).
    val viewModel: RetirarViewModel = viewModel()
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Surface(color = Color(0xFF0D47A1)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(1 to "Retirar", 2 to "Retirado").forEach { (n, texto) ->
                    TextButton(
                        onClick = { seccion = n },
                        modifier = Modifier.padding(vertical = 6.dp)
                            .background(if (seccion == n) Color.White.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(8.dp))
                    ) { Text(texto, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onSalir) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir", tint = Color.White)
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (seccion) {
                1 -> RetirarScreen(viewModel)
                2 -> RetiradoScreen(viewModel)
                else -> Unit
            }
        }
    }
}
