package com.example.magatzem

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.magatzem.ui.cierres.CierreImportViewModel
import com.example.magatzem.ui.nav.AppNavGraph
import com.example.magatzem.ui.theme.MagatzemTheme

class MainActivity : ComponentActivity() {
    private val cierreImport: CierreImportViewModel by viewModels()

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

    /** "Compartir" desde MiTPV (cierre de caja): el fichero se importa en cuanto haya sesión (ver AppNavGraph). */
    private fun recibirFichero(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) cierreImport.recibir(uri)
        // Para no reprocesar el mismo envío al girar la pantalla o recrear la actividad.
        intent.action = null
    }
}
