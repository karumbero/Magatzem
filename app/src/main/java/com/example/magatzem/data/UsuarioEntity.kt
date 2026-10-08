package com.example.magatzem.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** PIN con el que se crea cualquier usuario nuevo; debe cambiarse en el primer inicio de sesión. */
const val PIN_POR_DEFECTO = "123456"

@Entity(
    tableName = "usuarios",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class UsuarioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombre: String,
    /**
     * 6 dígitos. Puede repetirse entre usuarios (p. ej. dos altas recientes con el PIN por
     * defecto sin cambiar todavía): en el login se elige el usuario y luego se valida su PIN.
     */
    val pin: String,
    /** 1 dígito: nivel de acceso/permisos. */
    val nivel: Int,
    /** Obliga a cambiar el PIN antes de dejar entrar (usuarios recién creados, con el PIN por defecto). */
    val debeCambiarPin: Boolean = false,
    /** Un usuario desactivado no puede entrar pero se conserva. Los de nivel 1 no se borran: se desactivan (se sincronizan con MiTPV). */
    val activo: Boolean = true,
    /**
     * Cuándo se cambió por última vez (milisegundos). Los usuarios de nivel 1 se sincronizan con MiTPV en las dos direcciones y, si se
     * cambian a la vez en los dos sitios, vale el cambio más reciente. Lo rellena la base de datos (disparadores) en cada cambio.
     */
    val modificadoEn: Long = 0,
    /** Identidad estable entre aparatos (los `id` autonuméricos son locales). */
    val uuid: String = UUID.randomUUID().toString()
)
