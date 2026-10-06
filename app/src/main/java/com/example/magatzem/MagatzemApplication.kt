package com.example.magatzem

import android.app.Application
import com.example.magatzem.data.AppDatabase
import com.example.magatzem.data.CatalogoExportador
import com.example.magatzem.data.DispositivoId

class MagatzemApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        cargarBaseDeDatosInicial()
    }

    /**
     * Carga de una base de datos ya hecha, para dispositivos donde no se puede copiar a mano (p. ej. un Chromebook, sin acceso
     * a los datos de la app): si el APK trae `assets/seed/magatzem.db`, pasa a ser la base de datos la primera vez que se
     * arranca. Un APK normal no trae ese fichero y esto no hace nada.
     */
    private fun cargarBaseDeDatosInicial() {
        runCatching {
            val prefs = getSharedPreferences("semilla", MODE_PRIVATE)
            if (prefs.getBoolean("aplicada", false)) return
            val destino = getDatabasePath("magatzem.db")
            destino.parentFile?.mkdirs()
            assets.open("seed/magatzem.db").use { entrada ->
                listOf("", "-wal", "-shm", "-journal").forEach { java.io.File(destino.path + it).delete() }
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            }
            prefs.edit().putBoolean("aplicada", true).apply()
        }
    }

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val catalogoExportador: CatalogoExportador by lazy {
        CatalogoExportador(this, database, DispositivoId.obtener(this))
    }
}
