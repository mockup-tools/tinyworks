package io.github.mockuptools.tinyworks.feature.poyday

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.mockuptools.tinyworks.core.storage.TinyworksDatabase
import io.github.mockuptools.tinyworks.core.storage.TinyworksDatabaseModule

private object PoyDayDatabaseModule : TinyworksDatabaseModule {
    override val moduleName: String = "poyday"

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS poyday_rules (
                category TEXT PRIMARY KEY NOT NULL,
                enabled INTEGER NOT NULL,
                mode TEXT NOT NULL,
                week_pattern TEXT,
                weekday INTEGER,
                day_of_month INTEGER,
                updated_at_epoch_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        val now = System.currentTimeMillis()
        PoyDayCategory.entries.forEach { category ->
            database.execSQL(
                """
                INSERT OR IGNORE INTO poyday_rules
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

class PoyDayRepository(context: Context) {
    private val database = TinyworksDatabase.getInstance(
        context = context.applicationContext,
        modules = listOf(PoyDayDatabaseModule),
    )

    fun loadRules(): List<PoyDayRule> {
        val loadedRules = linkedMapOf<PoyDayCategory, PoyDayRule>()
        database.readableDatabase.query(
            TABLE_NAME,
            COLUMNS,
            null,
            null,
            null,
            null,
            CATEGORY_COLUMN,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val category = cursor.getString(0).toEnumOrNull<PoyDayCategory>() ?: continue
                loadedRules[category] = PoyDayRule(
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
        return PoyDayCategory.entries.map { category ->
            loadedRules[category] ?: PoyDayRule(category)
        }
    }

    fun saveRules(rules: Collection<PoyDayRule>) {
        val writableDatabase = database.writableDatabase
        writableDatabase.beginTransaction()
        try {
            rules.forEach(::saveRule)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun saveRule(rule: PoyDayRule) {
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
        database.writableDatabase.insertWithOnConflict(
            TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private companion object {
        const val TABLE_NAME = "poyday_rules"
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
