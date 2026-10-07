package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Ajustes sueltos de la tienda (clave → valor), guardados en la base de datos para que viajen con las copias. */
@Entity(tableName = "ajustes")
data class AjusteEntity(@PrimaryKey val clave: String, val valor: String)

/** Importe con el que se abre la caja cada día (el fondo de caja), en euros. */
const val AJUSTE_CAJA_INICIAL = "caja_inicial"

@Dao
interface AjusteDao {
    @Query("SELECT valor FROM ajustes WHERE clave = :clave")
    fun observeValor(clave: String): Flow<String?>

    @Query("SELECT valor FROM ajustes WHERE clave = :clave")
    suspend fun valor(clave: String): String?

    @Query("INSERT OR REPLACE INTO ajustes (clave, valor) VALUES (:clave, :valor)")
    suspend fun guardar(clave: String, valor: String)
}
