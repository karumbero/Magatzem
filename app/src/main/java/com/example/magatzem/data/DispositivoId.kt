package com.example.magatzem.data

import android.content.Context
import java.util.UUID

/** Identificador estable de este aparato, generado la primera vez. Marca el `origen` de la exportación. */
object DispositivoId {
    private const val PREFS = "magatzem_prefs"
    private const val KEY = "dispositivo_id"

    fun obtener(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY, it).apply()
        }
    }
}
