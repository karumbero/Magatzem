package com.example.magatzem.data

import android.content.Context

private const val PREFS_NAME = "sesion"
private const val KEY_NOMBRE = "nombre"
private const val KEY_NIVEL = "nivel"

/** Guarda quién ha iniciado sesión (nombre y nivel) para que la app se abra sola hasta pulsar Salir. */
class SesionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun obtenerNombre(): String? = prefs.getString(KEY_NOMBRE, null)

    fun obtenerNivel(): Int? = if (prefs.contains(KEY_NIVEL)) prefs.getInt(KEY_NIVEL, 0) else null

    fun guardar(nombre: String, nivel: Int) {
        prefs.edit().putString(KEY_NOMBRE, nombre).putInt(KEY_NIVEL, nivel).apply()
    }

    fun borrar() {
        prefs.edit().clear().apply()
    }
}
