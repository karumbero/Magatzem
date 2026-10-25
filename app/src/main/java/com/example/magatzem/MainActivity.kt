package com.example.magatzem

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.magatzem.ui.nav.AppNavGraph
import com.example.magatzem.ui.theme.MagatzemTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MagatzemTheme {
                AppNavGraph()
            }
        }
        recibirFichero(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recibirFichero(intent)
    }

    /** Una factura que manda Escaneo (el JSON viaja en el propio envío): se revisa y se le da entrada en cuanto haya sesión (ver AppNavGraph). */
    private fun recibirFichero(intent: Intent?) {
        if (intent?.action != com.example.magatzem.data.escaneo.ACCION_IMPORTAR_ESCANEO) return
        com.example.magatzem.data.escaneo.ImportacionEscaneo.recibir(intent.getStringExtra(com.example.magatzem.data.escaneo.EXTRA_JSON))
        intent.action = null
    }
}
