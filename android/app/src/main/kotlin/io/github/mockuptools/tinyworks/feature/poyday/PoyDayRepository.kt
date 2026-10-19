package io.github.mockuptools.tinyworks.feature.poyday

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.mockuptools.tinyworks.core.storage.TinyworksDatabase
import io.github.mockuptools.tinyworks.core.storage.TinyworksDatabaseModule

private object PoyDayDatabaseModule : TinyworksDatabaseModule {
    override val moduleName: String = "poyday"

    override fun onCreate(database: SQLiteDatabase) {
        if (tableExists(database, TABLE_NAME) && !hasColumn(database, TABLE_NAME, ID_COLUMN)) {
            migrateLegacyRules(database)
        } else {
            createRulesTable(database)
        }
        seedMissingRules(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            if (tableExists(database, TABLE_NAME)) {
                migrateLegacyRules(database)
            } else {
                createRulesTable(database)
            }
        }
        seedMissingRules(database)
    }

    private fun createRulesTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS poyday_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                mode TEXT NOT NULL,
                week_pattern TEXT,
                weekday INTEGER,
                day_of_month INTEGER,
                updated_at_epoch_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun seedMissingRules(database: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        PoyDayCategory.entries.forEach { category ->
            database.query(
                TABLE_NAME,
                arrayOf(ID_COLUMN),
                "$CATEGORY_COLUMN = ?",
                arrayOf(category.name),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    database.execSQL(
                        """
                        INSERT INTO poyday_rules
                            (category, enabled, mode, week_pattern, weekday, day_of_month, updated_at_epoch_millis)
                        VALUES (?, 0, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any>(
                            category.name,
                            PoyDayMode.WEEK.name,
                            PoyDayWeekPattern.EVERY.name,
                            PoyDayWeekday.SUNDAY.ordinal,
                            1,
                            now,
                        ),
                    )
                }
            }
        }
    }

    private fun migrateLegacyRules(database: SQLiteDatabase) {
        database.execSQL("ALTER TABLE $TABLE_NAME RENAME TO ${TABLE_NAME}_legacy")
        createRulesTable(database)
        database.query(
            "${TABLE_NAME}_legacy",
            LEGACY_COLUMNS,
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                database.execSQL(
                    """
                    INSERT INTO poyday_rules
                        (category, enabled, mode, week_pattern, weekday, day_of_month, updated_at_epoch_millis)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        cursor.getString(0),
                        cursor.getInt(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        if (cursor.isNull(4)) null else cursor.getInt(4),
                        if (cursor.isNull(5)) null else cursor.getInt(5),
                        cursor.getLong(6),
                    ),
                )
            }
        }
        database.execSQL("DROP TABLE ${TABLE_NAME}_legacy")
    }

    private fun tableExists(database: SQLiteDatabase, tableName: String): Boolean =
        database.query(
            "sqlite_master",
            arrayOf("name"),
            "type = ? AND name = ?",
            arrayOf("table", tableName),
            null,
            null,
            null,
        ).use { cursor -> cursor.moveToFirst() }

    private fun hasColumn(
        database: SQLiteDatabase,
        tableName: String,
        columnName: String,
    ): Boolean =
        database.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                    return true
                }
            }
            false
        }

    private const val TABLE_NAME = "poyday_rules"
    private const val ID_COLUMN = "id"
    private const val CATEGORY_COLUMN = "category"
    private val LEGACY_COLUMNS = arrayOf(
        CATEGORY_COLUMN,
        "enabled",
        "mode",
        "week_pattern",
        "weekday",
        "day_of_month",
        "updated_at_epoch_millis",
    )
}

class PoyDayRepository(context: Context) {
    private val database = TinyworksDatabase.getInstance(
        context = context.applicationContext,
        modules = listOf(PoyDayDatabaseModule),
    )

    fun loadRules(): List<PoyDayRule> {
        val loadedRules = mutableListOf<PoyDayRule>()
        database.readableDatabase.query(
            TABLE_NAME,
            COLUMNS,
            null,
            null,
            null,
            null,
            "$CATEGORY_COLUMN ASC, $ID_COLUMN ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val category = cursor.getString(0).toEnumOrNull<PoyDayCategory>() ?: continue
                loadedRules += PoyDayRule(
                    category = category,
                    enabled = cursor.getInt(1) != 0,
                    mode = cursor.getString(2).toEnumOrNull() ?: PoyDayMode.WEEK,
                    weekPattern = cursor.getString(3)?.toEnumOrNull() ?: PoyDayWeekPattern.EVERY,
                    weekday = cursor.getIntOrNull(4)?.let { ordinal ->
                        PoyDayWeekday.entries.getOrNull(ordinal)
                    } ?: PoyDayWeekday.SUNDAY,
                    dayOfMonth = cursor.getIntOrNull(5)?.coerceIn(1, 31) ?: 1,
                )
            }
        }
        return PoyDayCategory.entries.flatMap { category ->
            loadedRules.filter { it.category == category }.ifEmpty {
                listOf(PoyDayRule(category))
            }
        }
    }

    fun saveRules(rules: Collection<PoyDayRule>) {
        val writableDatabase = database.writableDatabase
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(TABLE_NAME, null, null)
            rules.forEach { rule ->
                val values = ContentValues().apply {
                    put(CATEGORY_COLUMN, rule.category.name)
                    put(ENABLED_COLUMN, if (rule.enabled) 1 else 0)
                    put(MODE_COLUMN, rule.mode.name)
                    put(
                        WEEK_PATTERN_COLUMN,
                        if (rule.mode == PoyDayMode.WEEK) rule.weekPattern.name else null,
                    )
                    put(
                        WEEKDAY_COLUMN,
                        if (rule.mode == PoyDayMode.WEEK) rule.weekday.ordinal else null,
                    )
                    put(
                        DAY_OF_MONTH_COLUMN,
                        if (rule.mode == PoyDayMode.DAY_OF_MONTH) rule.dayOfMonth.coerceIn(1, 31) else null,
                    )
                    put(UPDATED_AT_COLUMN, System.currentTimeMillis())
                }
                writableDatabase.insertOrThrow(TABLE_NAME, null, values)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private companion object {
        const val TABLE_NAME = "poyday_rules"
        const val ID_COLUMN = "id"
        const val CATEGORY_COLUMN = "category"
        const val ENABLED_COLUMN = "enabled"
        const val MODE_COLUMN = "mode"
        const val WEEK_PATTERN_COLUMN = "week_pattern"
        const val WEEKDAY_COLUMN = "weekday"
        const val DAY_OF_MONTH_COLUMN = "day_of_month"
        const val UPDATED_AT_COLUMN = "updated_at_epoch_millis"

        val COLUMNS = arrayOf(
            CATEGORY_COLUMN,
            ENABLED_COLUMN,
            MODE_COLUMN,
            WEEK_PATTERN_COLUMN,
            WEEKDAY_COLUMN,
            DAY_OF_MONTH_COLUMN,
        )
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

private fun android.database.Cursor.getIntOrNull(index: Int): Int? =
    if (isNull(index)) null else getInt(index)
