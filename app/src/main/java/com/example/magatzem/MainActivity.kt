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
        // Una factura que manda Escaneo (el JSON viaja en el propio envío): se revisa y se le da entrada en cuanto haya sesión.
        if (intent?.action == com.example.magatzem.data.escaneo.ACCION_IMPORTAR_ESCANEO) {
            com.example.magatzem.data.escaneo.ImportacionEscaneo.recibir(intent.getStringExtra(com.example.magatzem.data.escaneo.EXTRA_JSON))
            intent.action = null
            return
        }
        // Compartir (SEND, el archivo viene como EXTRA_STREAM) o "Abrir con" (VIEW, el archivo viene en data).
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_VIEW) return
        @Suppress("DEPRECATION")
        val uri: Uri? = if (intent.action == Intent.ACTION_VIEW) {
            intent.data
        } else if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) cierreImport.recibir(uri)
        // Para no reprocesar el mismo envío al girar la pantalla o recrear la actividad.
        intent.action = null
    }
}
