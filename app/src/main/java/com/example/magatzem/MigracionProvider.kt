package com.example.magatzem

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Solo lectura, para la app «Migracion» (que pasa el catálogo de Magatzem al TPV). Protegido con un permiso de firma: solo lo puede usar una app firmada
 * con la misma clave. content://com.example.magatzem.migracion/{productos|categorias|proveedores}
 */
class MigracionProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val tabla = uri.lastPathSegment?.takeIf { it in setOf("productos", "categorias", "proveedores") } ?: return null
        val app = context?.applicationContext as? MagatzemApplication ?: return null
        return app.database.openHelper.readableDatabase.query("SELECT * FROM `$tabla` ORDER BY id")
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
