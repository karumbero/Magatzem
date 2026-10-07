package com.example.magatzem.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** UUID v4 generado en SQL, distinto por fila (randomblob se evalúa fila a fila). */
private const val SQL_UUID =
    "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

/**
 * Datos de arranque (2026-09-29): la app se entrega lista para usar desde el primer momento, sin
 * pasar por el asistente de "crear primer usuario / datos de empresa / banco" (ver
 * `SesionViewModel`) — se siembran directamente al crear la base de datos, una sola vez, la primera
 * vez que se instala (si algún día se necesitara reponer solo Contado/Transferencia, para eso está
 * `FormaPagoDao.insertarSiNoExiste`, que es seguro llamar en cualquier momento).
 */
private val SEMILLA_INICIAL = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL(
            "INSERT INTO usuarios (nombre, pin, nivel, debeCambiarPin, uuid) " +
                "VALUES ('Mar', '123456', 1, 0, $SQL_UUID)"
        )
        db.execSQL("INSERT INTO formas_pago (nombre, activo, iban, titular, uuid) VALUES ('$NOMBRE_CONTADO', 1, NULL, NULL, $SQL_UUID)")
        db.execSQL("INSERT INTO formas_pago (nombre, activo, iban, titular, uuid) VALUES ('$NOMBRE_TRANSFERENCIA', 1, NULL, NULL, $SQL_UUID)")
        db.execSQL("INSERT INTO formas_pago (nombre, activo, iban, titular, uuid) VALUES ('Banca March', 1, NULL, NULL, $SQL_UUID)")
        db.execSQL(
            "INSERT INTO datos_empresa (id, nombre, nombreComercial) VALUES (1, 'Maria Llull Rosselló', 'Sa Platgeta')"
        )
    }
}

/**
 * SKU deja de ser obligatorio (ni tampoco categoría/proveedor, pero esos ya eran `Long?` desde el
 * principio — solo lo bloqueaba la validación del formulario, sin tocar esquema). SQLite no permite
 * cambiar NOT NULL de una columna con ALTER TABLE, así que se reconstruye la tabla entera (técnica
 * estándar): tabla nueva con `sku` sin NOT NULL, se copian los datos tal cual, se borra la vieja y
 * se renombra. No se pierde ningún dato existente.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `productos_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sku` TEXT, `codigoBarras` TEXT, `referenciaFabricante` TEXT, `nombre` TEXT NOT NULL, " +
                "`categoriaId` INTEGER, `proveedorId` INTEGER, `tipo` TEXT NOT NULL, `coste` REAL NOT NULL, " +
                "`margenBeneficio` REAL NOT NULL, `precioVenta` REAL NOT NULL, `existencia` INTEGER NOT NULL, " +
                "`minimo` INTEGER NOT NULL, `maximo` INTEGER, `fechaCreacion` TEXT NOT NULL, " +
                "`fechaActualizacion` TEXT NOT NULL, `uuid` TEXT NOT NULL, " +
                "FOREIGN KEY(`categoriaId`) REFERENCES `categorias`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`proveedorId`) REFERENCES `proveedores`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("INSERT INTO `productos_new` SELECT * FROM `productos`")
        db.execSQL("DROP TABLE `productos`")
        db.execSQL("ALTER TABLE `productos_new` RENAME TO `productos`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_productos_sku` ON `productos` (`sku`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_productos_codigoBarras` ON `productos` (`codigoBarras`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_productos_uuid` ON `productos` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_productos_categoriaId` ON `productos` (`categoriaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_productos_proveedorId` ON `productos` (`proveedorId`)")
    }
}

/** Pedidos a proveedor: tablas nuevas del todo, no tocan ninguna de las ya existentes. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pedidos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `proveedorId` INTEGER, `fecha` TEXT NOT NULL, `estado` TEXT NOT NULL, " +
                "`importe` REAL NOT NULL, `importeRecargo` REAL NOT NULL, `importeIva` REAL NOT NULL, " +
                "`total` REAL NOT NULL, FOREIGN KEY(`proveedorId`) REFERENCES `proveedores`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pedidos_uuid` ON `pedidos` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pedidos_proveedorId` ON `pedidos` (`proveedorId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pedido_lineas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`pedidoId` INTEGER NOT NULL, `productoId` INTEGER NOT NULL, `cantidad` INTEGER NOT NULL, " +
                "`coste` REAL NOT NULL, `subtotal` REAL NOT NULL, " +
                "FOREIGN KEY(`pedidoId`) REFERENCES `pedidos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`productoId`) REFERENCES `productos`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pedido_lineas_pedidoId` ON `pedido_lineas` (`pedidoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pedido_lineas_productoId` ON `pedido_lineas` (`productoId`)")
    }
}

/** Exportación al programa de ventas: tabla nueva del todo, no toca ninguna de las ya existentes. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exportaciones_catalogo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`fecha` TEXT NOT NULL, `dispositivoId` TEXT NOT NULL, `cantidadArticulos` INTEGER NOT NULL, " +
                "`nombreArchivo` TEXT NOT NULL)"
        )
    }
}

/**
 * Subcategorías (2 niveles): `categorias` gana `parentId`, con FK a sí misma. SQLite no permite
 * añadir una FOREIGN KEY con ALTER TABLE, así que se reconstruye la tabla entera (misma técnica que
 * MIGRATION_1_2): tabla nueva con la columna de más, se copian los datos (todas las categorías
 * existentes quedan de primer nivel, `parentId` NULL — nadie tenía subcategorías todavía), se borra
 * la vieja y se renombra. Los `id` no cambian, así que `productos.categoriaId` sigue apuntando bien.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `categorias_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`nombre` TEXT NOT NULL, `uuid` TEXT NOT NULL, `parentId` INTEGER, " +
                "FOREIGN KEY(`parentId`) REFERENCES `categorias`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL(
            "INSERT INTO `categorias_new` (`id`, `nombre`, `uuid`, `parentId`) " +
                "SELECT `id`, `nombre`, `uuid`, NULL FROM `categorias`"
        )
        db.execSQL("DROP TABLE `categorias`")
        db.execSQL("ALTER TABLE `categorias_new` RENAME TO `categorias`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categorias_nombre` ON `categorias` (`nombre`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categorias_uuid` ON `categorias` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categorias_parentId` ON `categorias` (`parentId`)")
    }
}

/** Entradas (Movimientos → Entradas): solo tablas nuevas, no toca ninguna existente. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recepciones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`proveedorId` INTEGER, `fecha` TEXT NOT NULL, `totalConIva` REAL NOT NULL, " +
                "`estadoFacturacion` TEXT NOT NULL, " +
                "FOREIGN KEY(`proveedorId`) REFERENCES `proveedores`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recepciones_proveedorId` ON `recepciones` (`proveedorId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recepcion_lineas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`recepcionId` INTEGER NOT NULL, `productoId` INTEGER NOT NULL, `cantidad` INTEGER NOT NULL, " +
                "`coste` REAL NOT NULL, `margenBeneficio` REAL NOT NULL, `subtotalConIva` REAL NOT NULL, " +
                "FOREIGN KEY(`recepcionId`) REFERENCES `recepciones`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`productoId`) REFERENCES `productos`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recepcion_lineas_recepcionId` ON `recepcion_lineas` (`recepcionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recepcion_lineas_productoId` ON `recepcion_lineas` (`productoId`)")
        val fkRecepcion =
            "FOREIGN KEY(`recepcionId`) REFERENCES `recepciones`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `albaranes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`recepcionId` INTEGER, `numero` TEXT, `fecha` TEXT NOT NULL, `base` REAL, `iva` REAL, " +
                "`uuid` TEXT NOT NULL, $fkRecepcion"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `facturas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`recepcionId` INTEGER, `numero` TEXT, `fecha` TEXT NOT NULL, `base` REAL NOT NULL, " +
                "`iva` REAL NOT NULL, `estado` TEXT NOT NULL, `uuid` TEXT NOT NULL, $fkRecepcion"
        )
        for (tabla in listOf("albaranes", "facturas")) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${tabla}_recepcionId` ON `$tabla` (`recepcionId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${tabla}_uuid` ON `$tabla` (`uuid`)")
        }
    }
}

/**
 * Pre-Stock (tabla `entradas_tienda`) se retiró de Entradas antes de llegar a la Lenovo: la 5→6
 * ya no la crea y esta migración solo la borra donde se hubiera llegado a crear (el Honor de
 * pruebas). `IF EXISTS` la hace segura en ambos caminos y no toca ninguna otra tabla ni dato.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `entradas_tienda`")
    }
}

/** Cambios y borrados de categorías/proveedores/artículos también se exportan: la exportación guarda su huella. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exportaciones_catalogo ADD COLUMN huellaMaestros TEXT")
    }
}

/** Importación de cierres de caja de MiTPV: solo tablas nuevas (cierres, retiradas, tickets y líneas). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cierres_importados` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `origen` TEXT NOT NULL, `abiertoEn` TEXT, `cerradoEn` TEXT NOT NULL, " +
                "`cajeroNombre` TEXT NOT NULL, `saldoInicial` REAL NOT NULL, `ventaContadoTickets` REAL NOT NULL, " +
                "`ventaTarjetaTickets` REAL NOT NULL, `totalDatafono` REAL NOT NULL, `ajusteTarjeta` REAL NOT NULL, " +
                "`ventaContado` REAL NOT NULL, `ventaTarjeta` REAL NOT NULL, `retiradoBanco` REAL NOT NULL, " +
                "`efectivoEsperado` REAL NOT NULL, `efectivoContado` REAL NOT NULL, `diferencia` REAL NOT NULL, " +
                "`numTickets` INTEGER NOT NULL, `primerTicket` INTEGER, `ultimoTicket` INTEGER, " +
                "`nombreArchivo` TEXT, `importadoEn` TEXT NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cierres_importados_uuid` ON `cierres_importados` (`uuid`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `retiradas_importadas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `cierreId` INTEGER NOT NULL, `fecha` TEXT NOT NULL, `importe` REAL NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_retiradas_importadas_uuid` ON `retiradas_importadas` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_retiradas_importadas_cierreId` ON `retiradas_importadas` (`cierreId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ventas_importadas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `cierreId` INTEGER NOT NULL, `numero` INTEGER NOT NULL, `fecha` TEXT NOT NULL, " +
                "`formaPago` TEXT NOT NULL, `importeIva` REAL NOT NULL, `importeTotal` REAL NOT NULL, `ventaOriginalUuid` TEXT)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ventas_importadas_uuid` ON `ventas_importadas` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ventas_importadas_cierreId` ON `ventas_importadas` (`cierreId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `venta_lineas_importadas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `ventaId` INTEGER NOT NULL, `productoId` INTEGER, `productoUuid` TEXT, `sku` TEXT, " +
                "`codigoBarras` TEXT, `descripcion` TEXT NOT NULL, `cantidad` INTEGER NOT NULL, " +
                "`precioUnitarioConIva` REAL NOT NULL, `ivaPorcentaje` REAL NOT NULL, `subtotalConIva` REAL NOT NULL, " +
                "`costeUnitario` REAL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_venta_lineas_importadas_uuid` ON `venta_lineas_importadas` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_venta_lineas_importadas_ventaId` ON `venta_lineas_importadas` (`ventaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_venta_lineas_importadas_productoId` ON `venta_lineas_importadas` (`productoId`)")
    }
}

/**
 * Oficina, incidencias y caja/bancos: tablas nuevas (incidencias, pagos de facturas, movimientos de caja y de
 * banco) y la columna `principal` de formas de pago (con 0 por defecto). No se toca ningún dato existente.
 */
/** v11: proveedores exentos de IVA y recargo (sellos de Correos). No toca ningún dato existente. */
/** v15: ajustes sueltos (clave → valor), p. ej. la caja inicial. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `ajustes` (`clave` TEXT NOT NULL, `valor` TEXT NOT NULL, PRIMARY KEY(`clave`))")
    }
}

/** v14: inventarios (recuentos físicos) con sus líneas. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `inventarios` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`nombre` TEXT NOT NULL, `fecha` TEXT NOT NULL, `estado` TEXT NOT NULL, `fechaCierre` TEXT)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventarios_uuid` ON `inventarios` (`uuid`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `inventario_lineas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `inventarioId` INTEGER NOT NULL, " +
                "`productoId` INTEGER NOT NULL, `sku` TEXT, `referencia` TEXT, `codigoBarras` TEXT, `nombre` TEXT NOT NULL, `categoria` TEXT, " +
                "`proveedor` TEXT, `existenciaInicial` INTEGER NOT NULL, `bajas` INTEGER NOT NULL, `contadas` INTEGER, " +
                "`existenciaAlContar` INTEGER, `fechaConteo` TEXT, " +
                "FOREIGN KEY(`inventarioId`) REFERENCES `inventarios`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventario_lineas_inventarioId_productoId` ON `inventario_lineas` (`inventarioId`, `productoId`)")
    }
}

/** v13: capas de coste (FIFO): lo gastado de cada línea de entrada. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `consumos_capa` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recepcionId` INTEGER NOT NULL, " +
                "`productoId` INTEGER NOT NULL, `cantidad` INTEGER NOT NULL, `tipo` TEXT NOT NULL, `ventaLineaId` INTEGER, " +
                "`incidenciaId` INTEGER, `fecha` TEXT NOT NULL, " +
                "FOREIGN KEY(`recepcionId`) REFERENCES `recepciones`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_consumos_capa_recepcionId_productoId` ON `consumos_capa` (`recepcionId`, `productoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_consumos_capa_ventaLineaId` ON `consumos_capa` (`ventaLineaId`)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `productos` ADD COLUMN `mostrarEnTeclado` INTEGER NOT NULL DEFAULT 1")
        // Los artículos con código de barras se venden escaneando: no salen en las teclas programables.
        db.execSQL("UPDATE `productos` SET `mostrarEnTeclado` = 0 WHERE `codigoBarras` IS NOT NULL AND `codigoBarras` <> ''")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `proveedores` ADD COLUMN `exentoIva` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `incidencias` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`productoId` INTEGER, `productoNombre` TEXT NOT NULL, `productoSku` TEXT, `tipo` TEXT NOT NULL, " +
                "`cantidad` INTEGER NOT NULL, `costeUnitario` REAL NOT NULL, `fecha` TEXT NOT NULL, `nota` TEXT, " +
                "FOREIGN KEY(`productoId`) REFERENCES `productos`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_incidencias_productoId` ON `incidencias` (`productoId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_incidencias_uuid` ON `incidencias` (`uuid`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pagos_factura` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`facturaId` INTEGER NOT NULL, `formaPagoId` INTEGER, `numeroPlazo` INTEGER NOT NULL, `totalPlazos` INTEGER NOT NULL, " +
                "`importe` REAL NOT NULL, `fechaPrevista` TEXT NOT NULL, `fechaPago` TEXT, " +
                "FOREIGN KEY(`facturaId`) REFERENCES `facturas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`formaPagoId`) REFERENCES `formas_pago`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pagos_factura_facturaId` ON `pagos_factura` (`facturaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pagos_factura_formaPagoId` ON `pagos_factura` (`formaPagoId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pagos_factura_uuid` ON `pagos_factura` (`uuid`)")
        // Banco principal (cobros con tarjeta e ingresos de caja) y los libros de caja y bancos.
        db.execSQL("ALTER TABLE `formas_pago` ADD COLUMN `principal` INTEGER NOT NULL DEFAULT 0")
        // Las salidas de caja de los cierres pueden ser traspasos a banco o pagos a proveedor.
        db.execSQL("ALTER TABLE `retiradas_importadas` ADD COLUMN `tipo` TEXT NOT NULL DEFAULT 'BANCO'")
        db.execSQL("ALTER TABLE `retiradas_importadas` ADD COLUMN `nota` TEXT")
        db.execSQL("ALTER TABLE `retiradas_importadas` ADD COLUMN `proveedorUuid` TEXT")
        db.execSQL("ALTER TABLE `retiradas_importadas` ADD COLUMN `numeroFactura` TEXT")
        db.execSQL("ALTER TABLE `retiradas_importadas` ADD COLUMN `pagoFacturaId` INTEGER")
        // Datos para informes de ventas: cajero del ticket y proveedor/categoría de cada línea en su día.
        db.execSQL("ALTER TABLE `ventas_importadas` ADD COLUMN `cajeroUuid` TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS `cajeros` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `nombre` TEXT NOT NULL, `ultimaActividad` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cajeros_uuid` ON `cajeros` (`uuid`)")
        // Fechas de albaranes, facturas y pagos: de dd-MM-yyyy (texto) a ISO yyyy-MM-dd, ordenable y agrupable.
        db.execSQL("UPDATE `albaranes` SET `fecha` = substr(`fecha`, 7, 4) || '-' || substr(`fecha`, 4, 2) || '-' || substr(`fecha`, 1, 2) WHERE `fecha` GLOB '[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9]'")
        db.execSQL("UPDATE `facturas` SET `fecha` = substr(`fecha`, 7, 4) || '-' || substr(`fecha`, 4, 2) || '-' || substr(`fecha`, 1, 2) WHERE `fecha` GLOB '[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9]'")
        db.execSQL("UPDATE `pagos_factura` SET `fechaPrevista` = substr(`fechaPrevista`, 7, 4) || '-' || substr(`fechaPrevista`, 4, 2) || '-' || substr(`fechaPrevista`, 1, 2) WHERE `fechaPrevista` GLOB '[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9]'")
        db.execSQL("UPDATE `pagos_factura` SET `fechaPago` = substr(`fechaPago`, 7, 4) || '-' || substr(`fechaPago`, 4, 2) || '-' || substr(`fechaPago`, 1, 2) WHERE `fechaPago` GLOB '[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9]'")
        // Cajeros ya conocidos: los usuarios de Magatzem (vienen de MiTPV en la primera exportación).
        db.execSQL("INSERT OR IGNORE INTO `cajeros` (`uuid`, `nombre`, `ultimaActividad`) SELECT `uuid`, `nombre`, '' FROM `usuarios` WHERE `uuid` IS NOT NULL AND `uuid` <> ''")
        db.execSQL("ALTER TABLE `venta_lineas_importadas` ADD COLUMN `proveedorUuid` TEXT")
        db.execSQL("ALTER TABLE `venta_lineas_importadas` ADD COLUMN `categoriaUuid` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `movimientos_caja` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`fecha` TEXT NOT NULL, `concepto` TEXT NOT NULL, `importe` REAL NOT NULL, `cierreId` INTEGER)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movimientos_caja_cierreId` ON `movimientos_caja` (`cierreId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_movimientos_caja_uuid` ON `movimientos_caja` (`uuid`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `movimientos_banco` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                "`bancoId` INTEGER, `fecha` TEXT NOT NULL, `concepto` TEXT NOT NULL, `importe` REAL NOT NULL, `cierreId` INTEGER, `pagoId` INTEGER, " +
                "FOREIGN KEY(`bancoId`) REFERENCES `formas_pago`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`pagoId`) REFERENCES `pagos_factura`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movimientos_banco_bancoId` ON `movimientos_banco` (`bancoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movimientos_banco_cierreId` ON `movimientos_banco` (`cierreId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movimientos_banco_pagoId` ON `movimientos_banco` (`pagoId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_movimientos_banco_uuid` ON `movimientos_banco` (`uuid`)")
    }
}

/**
 * Los artículos de temporadas anteriores no tienen coste real (coste 0). Cuando a uno se le pone por primera vez un coste (por una
 * entrada, un albarán, una factura o editando el artículo), ese coste se anota también en sus ventas anteriores importadas que
 * quedaron sin coste, para que entren en los beneficios. Solo se tocan las líneas sin coste (nula o 0): el coste que ya tuvieran
 * otras ventas no cambia, ni tampoco el precio al que se vendió cada una. Se crea al abrir la base (siempre con los factores de
 * IVA y recargo actuales) y vale para cualquier forma de cambiar el coste.
 */
private val TRIGGER_COSTE_EN_VENTAS = object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS rellenar_coste_en_ventas")
        db.execSQL(
            "CREATE TRIGGER rellenar_coste_en_ventas AFTER UPDATE OF coste ON productos " +
                "WHEN OLD.coste <= 0 AND NEW.coste > 0 BEGIN " +
                "UPDATE venta_lineas_importadas SET costeUnitario = NEW.coste * " +
                "(CASE WHEN COALESCE((SELECT exentoIva FROM proveedores WHERE id = NEW.proveedorId), 0) = 1 " +
                "THEN ${factorCoste(true)} ELSE ${factorCoste(false)} END) " +
                "WHERE productoId = NEW.id AND (costeUnitario IS NULL OR costeUnitario <= 0); END"
        )
    }
}

@Database(
    entities = [
        UsuarioEntity::class,
        DatosEmpresaEntity::class,
        ProveedorEntity::class,
        CategoriaEntity::class,
        ProductoEntity::class,
        FormaPagoEntity::class,
        PedidoEntity::class,
        PedidoLineaEntity::class,
        ExportacionCatalogoEntity::class,
        RecepcionEntity::class,
        RecepcionLineaEntity::class,
        AlbaranEntity::class,
        FacturaEntity::class,
        CierreImportadoEntity::class,
        RetiradaImportadaEntity::class,
        VentaImportadaEntity::class,
        VentaLineaImportadaEntity::class,
        IncidenciaEntity::class,
        PagoFacturaEntity::class,
        MovimientoCajaEntity::class,
        MovimientoBancoEntity::class,
        CajeroEntity::class,
        ConsumoCapaEntity::class,
        InventarioEntity::class,
        InventarioLineaEntity::class,
        AjusteEntity::class
    ],
    version = 15,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao
    abstract fun datosEmpresaDao(): DatosEmpresaDao
    abstract fun proveedorDao(): ProveedorDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun productoDao(): ProductoDao
    abstract fun formaPagoDao(): FormaPagoDao
    abstract fun pedidoDao(): PedidoDao
    abstract fun exportacionCatalogoDao(): ExportacionCatalogoDao
    abstract fun recepcionDao(): RecepcionDao
    abstract fun cierreImportadoDao(): CierreImportadoDao
    abstract fun incidenciaDao(): IncidenciaDao
    abstract fun pagoFacturaDao(): PagoFacturaDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun cajeroDao(): CajeroDao
    abstract fun consumoCapaDao(): ConsumoCapaDao
    abstract fun inventarioDao(): InventarioDao
    abstract fun ajusteDao(): AjusteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "magatzem.db"
                )
                    .addCallback(SEMILLA_INICIAL)
                    .addCallback(TRIGGER_COSTE_EN_VENTAS)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .build().also { instance = it }
            }
    }
}
