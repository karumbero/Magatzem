package com.example.magatzem.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DatosEmpresaDao {
    @Query("SELECT * FROM datos_empresa WHERE id = 1")
    fun observe(): Flow<DatosEmpresaEntity?>

    @Query("SELECT * FROM datos_empresa WHERE id = 1")
    suspend fun obtener(): DatosEmpresaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(datos: DatosEmpresaEntity)
}
