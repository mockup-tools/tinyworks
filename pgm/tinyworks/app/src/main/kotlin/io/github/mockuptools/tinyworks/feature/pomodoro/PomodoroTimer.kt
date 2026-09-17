package io.github.mockuptools.tinyworks.feature.pomodoro

const val WORK_DURATION_MILLIS = 25 * 60 * 1000L
const val BREAK_DURATION_MILLIS = 5 * 60 * 1000L

enum class PomodoroPhase {
    WORK,
    BREAK,
}

enum class PomodoroStatus {
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
}

data class PomodoroTimerState(
    val phase: PomodoroPhase,
    val status: PomodoroStatus,
    val elapsedMillis: Long,
    val durationMillis: Long,
) {
    val remainingMillis: Long
        get() = (durationMillis - elapsedMillis).coerceAtLeast(0L)

    val overtimeMillis: Long
        get() = if (phase == PomodoroPhase.WORK) {
            (elapsedMillis - durationMillis).coerceAtLeast(0L)
        } else {
            0L
        }

    val isRunning: Boolean
        get() = status == PomodoroStatus.RUNNING
}

/**
 * Android UIやServiceから独立したPomodoroの状態機械。
 * nowMillisにはSystemClock.elapsedRealtime()などの単調増加する時刻を渡す。
 */
class PomodoroTimer(
    private val workDurationMillis: Long = WORK_DURATION_MILLIS,
    private val breakDurationMillis: Long = BREAK_DURATION_MILLIS,
) {
    private var phase = PomodoroPhase.WORK
    private var status = PomodoroStatus.READY
    private var elapsedMillis = 0L
    private var runningSinceMillis: Long? = null

    fun snapshot(): PomodoroTimerState = PomodoroTimerState(
        phase = phase,
        status = status,
        elapsedMillis = elapsedMillis,
        durationMillis = durationFor(phase),
    )

    fun advance(nowMillis: Long): PomodoroTimerState {
        if (status == PomodoroStatus.RUNNING) {
            val runningSince = runningSinceMillis ?: nowMillis
            elapsedMillis += (nowMillis - runningSince).coerceAtLeast(0L)
            runningSinceMillis = nowMillis

            if (phase == PomodoroPhase.BREAK && elapsedMillis >= breakDurationMillis) {
                elapsedMillis = breakDurationMillis
                status = PomodoroStatus.COMPLETED
                runningSinceMillis = null
            }
        }
        return snapshot()
    }

    fun start(nowMillis: Long): PomodoroTimerState {
        if (status == PomodoroStatus.READY) {
            status = PomodoroStatus.RUNNING
            runningSinceMillis = nowMillis
        }
        return snapshot()
    }

    fun pause(nowMillis: Long): PomodoroTimerState {
        if (status == PomodoroStatus.RUNNING) {
            advance(nowMillis)
            status = PomodoroStatus.PAUSED
            runningSinceMillis = null
        }
        return snapshot()
    }

    fun resume(nowMillis: Long): PomodoroTimerState {
        if (status == PomodoroStatus.PAUSED) {
            status = PomodoroStatus.RUNNING
            runningSinceMillis = nowMillis
        }
        return snapshot()
    }

    fun startBreak(nowMillis: Long): PomodoroTimerState {
        advance(nowMillis)
        if (
            phase == PomodoroPhase.WORK &&
            (status == PomodoroStatus.RUNNING || status == PomodoroStatus.PAUSED)
        ) {
            phase = PomodoroPhase.BREAK
            status = PomodoroStatus.RUNNING
            elapsedMillis = 0L
            runningSinceMillis = nowMillis
        }
        return snapshot()
    }

    fun startNextWork(nowMillis: Long): PomodoroTimerState {
        if (phase == PomodoroPhase.BREAK && status == PomodoroStatus.COMPLETED) {
            phase = PomodoroPhase.WORK
            status = PomodoroStatus.RUNNING
            elapsedMillis = 0L
            runningSinceMillis = nowMillis
        }
        return snapshot()
    }

    fun reset(): PomodoroTimerState {
        phase = PomodoroPhase.WORK
        status = PomodoroStatus.READY
        elapsedMillis = 0L
        runningSinceMillis = null
        return snapshot()
    }

    private fun durationFor(currentPhase: PomodoroPhase): Long = when (currentPhase) {
        PomodoroPhase.WORK -> workDurationMillis
        PomodoroPhase.BREAK -> breakDurationMillis
    }
}
