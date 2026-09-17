package io.github.mockuptools.tinyworks.feature.pomodoro

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.mockuptools.tinyworks.core.storage.TinyworksDatabase
import io.github.mockuptools.tinyworks.core.storage.TinyworksDatabaseModule

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MAX_HISTORY_ITEMS = 100

data class PomodoroDurations(
    val workMillis: Long = WORK_DURATION_MILLIS,
    val breakMillis: Long = BREAK_DURATION_MILLIS,
) {
    init {
        require(workMillis > 0L)
        require(breakMillis > 0L)
    }

    val workMinutes: Int
        get() = (workMillis / MILLIS_PER_MINUTE).toInt()

    val workSeconds: Int
        get() = ((workMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND).toInt()

    val breakMinutes: Int
        get() = (breakMillis / MILLIS_PER_MINUTE).toInt()

    val breakSeconds: Int
        get() = ((breakMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND).toInt()
}

data class PomodoroSessionRecord(
    val id: Long,
    val workStartedAtEpochMillis: Long,
    val workEndedAtEpochMillis: Long,
    val workElapsedMillis: Long,
    val overtimeMillis: Long,
    val breakStartedAtEpochMillis: Long,
    val breakEndedAtEpochMillis: Long?,
    val breakElapsedMillis: Long?,
)

data class PomodoroStatistics(
    val workSessionCount: Long,
    val completedBreakCount: Long,
    val totalWorkMillis: Long,
    val totalOvertimeMillis: Long,
)

private object PomodoroDatabaseModule : TinyworksDatabaseModule {
    override val moduleName: String = "pomodoro"

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pomodoro_settings (
                id INTEGER PRIMARY KEY,
                work_duration_millis INTEGER NOT NULL,
                break_duration_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT OR IGNORE INTO pomodoro_settings
                (id, work_duration_millis, break_duration_millis)
            VALUES (1, $WORK_DURATION_MILLIS, $BREAK_DURATION_MILLIS)
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pomodoro_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                work_started_at_epoch_millis INTEGER NOT NULL,
                work_ended_at_epoch_millis INTEGER NOT NULL,
                work_elapsed_millis INTEGER NOT NULL,
                overtime_millis INTEGER NOT NULL,
                break_started_at_epoch_millis INTEGER NOT NULL,
                break_ended_at_epoch_millis INTEGER,
                break_elapsed_millis INTEGER
            )
            """.trimIndent(),
        )
    }
}

class PomodoroRepository(context: Context) {
    private val database = TinyworksDatabase.getInstance(
        context = context.applicationContext,
        modules = listOf(PomodoroDatabaseModule),
    )

    fun loadDurations(): PomodoroDurations {
        database.readableDatabase.query(
            SETTINGS_TABLE,
            arrayOf(WORK_DURATION_COLUMN, BREAK_DURATION_COLUMN),
            "$ID_COLUMN = ?",
            arrayOf("1"),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return runCatching {
                    PomodoroDurations(
                        workMillis = cursor.getLong(0),
                        breakMillis = cursor.getLong(1),
                    )
                }.getOrDefault(PomodoroDurations())
            }
        }
        return PomodoroDurations()
    }

    fun saveDurations(durations: PomodoroDurations) {
        val values = ContentValues().apply {
            put(ID_COLUMN, 1L)
            put(WORK_DURATION_COLUMN, durations.workMillis)
            put(BREAK_DURATION_COLUMN, durations.breakMillis)
        }
        database.writableDatabase.insertWithOnConflict(
            SETTINGS_TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun recordWorkSession(
        workStartedAtEpochMillis: Long,
        workEndedAtEpochMillis: Long,
        workElapsedMillis: Long,
        overtimeMillis: Long,
        breakStartedAtEpochMillis: Long,
    ): Long {
        val values = ContentValues().apply {
            put(WORK_STARTED_COLUMN, workStartedAtEpochMillis)
            put(WORK_ENDED_COLUMN, workEndedAtEpochMillis)
            put(WORK_ELAPSED_COLUMN, workElapsedMillis)
            put(OVERTIME_COLUMN, overtimeMillis)
            put(BREAK_STARTED_COLUMN, breakStartedAtEpochMillis)
        }
        return database.writableDatabase.insertOrThrow(SESSIONS_TABLE, null, values)
    }

    fun completeBreak(
        sessionId: Long,
        breakEndedAtEpochMillis: Long,
        breakElapsedMillis: Long,
    ) {
        val values = ContentValues().apply {
            put(BREAK_ENDED_COLUMN, breakEndedAtEpochMillis)
            put(BREAK_ELAPSED_COLUMN, breakElapsedMillis)
        }
        database.writableDatabase.update(
            SESSIONS_TABLE,
            values,
            "$ID_COLUMN = ?",
            arrayOf(sessionId.toString()),
        )
    }

    fun deleteAllSessions() {
        database.writableDatabase.delete(SESSIONS_TABLE, null, null)
    }

    fun recentSessions(limit: Int = MAX_HISTORY_ITEMS): List<PomodoroSessionRecord> {
        val records = mutableListOf<PomodoroSessionRecord>()
        database.readableDatabase.query(
            SESSIONS_TABLE,
            SESSION_COLUMNS,
            null,
            null,
            null,
            null,
            "$WORK_STARTED_COLUMN DESC",
            limit.coerceIn(1, MAX_HISTORY_ITEMS).toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                records += PomodoroSessionRecord(
                    id = cursor.getLong(0),
                    workStartedAtEpochMillis = cursor.getLong(1),
                    workEndedAtEpochMillis = cursor.getLong(2),
                    workElapsedMillis = cursor.getLong(3),
                    overtimeMillis = cursor.getLong(4),
                    breakStartedAtEpochMillis = cursor.getLong(5),
                    breakEndedAtEpochMillis = cursor.getNullableLong(6),
                    breakElapsedMillis = cursor.getNullableLong(7),
                )
            }
        }
        return records
    }

    fun statistics(): PomodoroStatistics {
        database.readableDatabase.rawQuery(
            """
            SELECT
                COUNT(*),
                SUM(CASE WHEN $BREAK_ENDED_COLUMN IS NOT NULL THEN 1 ELSE 0 END),
                COALESCE(SUM($WORK_ELAPSED_COLUMN), 0),
                COALESCE(SUM($OVERTIME_COLUMN), 0)
            FROM $SESSIONS_TABLE
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return PomodoroStatistics(
                    workSessionCount = cursor.getLong(0),
                    completedBreakCount = cursor.getLong(1),
                    totalWorkMillis = cursor.getLong(2),
                    totalOvertimeMillis = cursor.getLong(3),
                )
            }
        }
        return PomodoroStatistics(0L, 0L, 0L, 0L)
    }

    private companion object {
        const val SETTINGS_TABLE = "pomodoro_settings"
        const val SESSIONS_TABLE = "pomodoro_sessions"
        const val ID_COLUMN = "id"
        const val WORK_DURATION_COLUMN = "work_duration_millis"
        const val BREAK_DURATION_COLUMN = "break_duration_millis"
        const val WORK_STARTED_COLUMN = "work_started_at_epoch_millis"
        const val WORK_ENDED_COLUMN = "work_ended_at_epoch_millis"
        const val WORK_ELAPSED_COLUMN = "work_elapsed_millis"
        const val OVERTIME_COLUMN = "overtime_millis"
        const val BREAK_STARTED_COLUMN = "break_started_at_epoch_millis"
        const val BREAK_ENDED_COLUMN = "break_ended_at_epoch_millis"
        const val BREAK_ELAPSED_COLUMN = "break_elapsed_millis"

        val SESSION_COLUMNS = arrayOf(
            ID_COLUMN,
            WORK_STARTED_COLUMN,
            WORK_ENDED_COLUMN,
            WORK_ELAPSED_COLUMN,
            OVERTIME_COLUMN,
            BREAK_STARTED_COLUMN,
            BREAK_ENDED_COLUMN,
            BREAK_ELAPSED_COLUMN,
        )
    }
}

private fun android.database.Cursor.getNullableLong(index: Int): Long? =
    if (isNull(index)) null else getLong(index)
