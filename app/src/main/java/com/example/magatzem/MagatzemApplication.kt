package com.example.magatzem

import android.app.Application
import kotlinx.coroutines.launch
import com.example.magatzem.data.AppDatabase
import com.example.magatzem.data.CatalogoExportador
import com.example.magatzem.data.DispositivoId

class MagatzemApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        cargarBaseDeDatosInicial()
        regularizarCapasIniciales()
    }

    /**
     * Una sola vez: a los artículos con existencia y coste que no tienen ninguna capa de coste (existencia tecleada en la ficha) se les crea su capa inicial. Solo añade entradas
     * («capa0»); no cambia ningún dato existente. Desde entonces, la ficha ya crea la capa al dar de alta un artículo con coste y existencia (ver [CapaInicial]).
     */
    private fun regularizarCapasIniciales() {
        val prefs = getSharedPreferences("mantenimiento", MODE_PRIVATE)
        if (prefs.getBoolean("capaInicialRegularizada", false)) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                val (articulos, unidades) = com.example.magatzem.data.CapaInicial.regularizar(database)
                prefs.edit().putBoolean("capaInicialRegularizada", true).putString("capaInicialResultado", "$articulos artículos, $unidades unidades").apply()
            }
        }
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
