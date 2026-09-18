package io.github.mockuptools.tinyworks.core.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 機能ごとのSQLiteスキーマを登録するための共通契約。
 * 業務ルールや機能固有のテーブル名は各feature側で定義する。
 */
interface TinyworksDatabaseModule {
    val moduleName: String

    fun onCreate(database: SQLiteDatabase)

    fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(database)
    }
}

class TinyworksDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    private val modules = linkedMapOf<String, TinyworksDatabaseModule>()

    fun register(module: TinyworksDatabaseModule) {
        modules[module.moduleName] = module
    }

    fun ensureSchemas() {
        val database = writableDatabase
        modules.values.forEach { it.onCreate(database) }
    }

    override fun onCreate(database: SQLiteDatabase) {
        modules.values.forEach { it.onCreate(database) }
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        modules.values.forEach { it.onUpgrade(database, oldVersion, newVersion) }
    }

    override fun onOpen(database: SQLiteDatabase) {
        super.onOpen(database)
        modules.values.forEach { it.onCreate(database) }
    }

    companion object Provider {
        private const val DATABASE_NAME = "tinyworks.db"
        private const val DATABASE_VERSION = 2

        private var instance: TinyworksDatabase? = null

        private fun get(
            context: Context,
            modules: Collection<TinyworksDatabaseModule>,
        ): TinyworksDatabase {
            val database = instance ?: TinyworksDatabase(context).also { instance = it }
            modules.forEach(database::register)
            database.ensureSchemas()
            return database
        }

        fun getInstance(
            context: Context,
            modules: Collection<TinyworksDatabaseModule>,
        ): TinyworksDatabase = synchronized(this) {
            get(context, modules)
        }
    }
}
