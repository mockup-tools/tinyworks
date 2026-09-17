package io.github.mockuptools.tinyworks.feature.pomodoro

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * ActivityとForeground Serviceが同じプロセス内で共有するPomodoroセッション。
 * 実行中の状態はメモリに保持し、設定と完了した作業セッションはRepositoryへ委譲する。
 */
object PomodoroSession {
    private var timer = PomodoroTimer()
    private var repository: PomodoroRepository? = null
    private var durations = PomodoroDurations()
    private var workStartedAtEpochMillis: Long? = null
    private var activeHistoryId: Long? = null

    private val mutableState = mutableStateOf(timer.snapshot())
    private val mutableStatistics = mutableStateOf(PomodoroStatistics(0L, 0L, 0L, 0L))
    private val mutableRecentSessions = mutableStateOf<List<PomodoroSessionRecord>>(emptyList())

    val state: State<PomodoroTimerState>
        get() = mutableState

    val currentDurations: PomodoroDurations
        get() = durations

    val statistics: State<PomodoroStatistics>
        get() = mutableStatistics

    val recentSessions: State<List<PomodoroSessionRecord>>
        get() = mutableRecentSessions

    fun initialize(context: Context) {
        if (repository != null) return

        val loadedRepository = PomodoroRepository(context.applicationContext)
        repository = loadedRepository
        durations = loadedRepository.loadDurations()
        timer = PomodoroTimer(
            workDurationMillis = durations.workMillis,
            breakDurationMillis = durations.breakMillis,
        )
        mutableState.value = timer.snapshot()
        refreshStatistics()
    }

    fun advance(nowMillis: Long = SystemClock.elapsedRealtime()): PomodoroTimerState {
        val before = timer.snapshot()
        val next = timer.advance(nowMillis)
        if (
            before.phase == PomodoroPhase.BREAK &&
            before.status == PomodoroStatus.RUNNING &&
            next.status == PomodoroStatus.COMPLETED
        ) {
            activeHistoryId?.let { historyId ->
                repository?.completeBreak(
                    sessionId = historyId,
                    breakEndedAtEpochMillis = System.currentTimeMillis(),
                    breakElapsedMillis = next.elapsedMillis,
                )
            }
            activeHistoryId = null
            refreshStatistics()
        }
        return publish(next)
    }

    fun start(nowMillis: Long = SystemClock.elapsedRealtime()): PomodoroTimerState {
        val before = timer.snapshot()
        val next = timer.start(nowMillis)
        if (
            before.phase == PomodoroPhase.WORK &&
            before.status == PomodoroStatus.READY &&
            next.status == PomodoroStatus.RUNNING
        ) {
            workStartedAtEpochMillis = System.currentTimeMillis()
        }
        return publish(next)
    }

    fun pause(nowMillis: Long = SystemClock.elapsedRealtime()): PomodoroTimerState =
        publish(timer.pause(nowMillis))

    fun resume(nowMillis: Long = SystemClock.elapsedRealtime()): PomodoroTimerState =
        publish(timer.resume(nowMillis))

    fun startBreak(nowMillis: Long = SystemClock.elapsedRealtime()): PomodoroTimerState {
        val workState = timer.advance(nowMillis)
        val next = timer.startBreak(nowMillis)
        if (
            workState.phase == PomodoroPhase.WORK &&
            next.phase == PomodoroPhase.BREAK &&
            (workState.status == PomodoroStatus.RUNNING ||
                workState.status == PomodoroStatus.PAUSED)
        ) {
            activeHistoryId = repository?.recordWorkSession(
                workStartedAtEpochMillis = workStartedAtEpochMillis ?: System.currentTimeMillis(),
                workEndedAtEpochMillis = System.currentTimeMillis(),
                workElapsedMillis = workState.elapsedMillis,
                overtimeMillis = workState.overtimeMillis,
                breakStartedAtEpochMillis = System.currentTimeMillis(),
            )
            refreshStatistics()
        }
        return publish(next)
    }

    fun startNextWork(nowMillis: Long = SystemClock.elapsedRealtime()): PomodoroTimerState {
        val next = timer.startNextWork(nowMillis)
        if (next.phase == PomodoroPhase.WORK && next.status == PomodoroStatus.RUNNING) {
            workStartedAtEpochMillis = System.currentTimeMillis()
            activeHistoryId = null
        }
        return publish(next)
    }

    fun updateDurations(newDurations: PomodoroDurations): PomodoroTimerState {
        if (timer.snapshot().status != PomodoroStatus.READY) return timer.snapshot()

        repository?.saveDurations(newDurations)
        durations = newDurations
        timer = PomodoroTimer(
            workDurationMillis = durations.workMillis,
            breakDurationMillis = durations.breakMillis,
        )
        return publish(timer.snapshot())
    }

    fun reset(): PomodoroTimerState {
        workStartedAtEpochMillis = null
        activeHistoryId = null
        return publish(timer.reset())
    }

    fun deleteStatistics() {
        activeHistoryId = null
        repository?.deleteAllSessions()
        refreshStatistics()
    }

    private fun refreshStatistics() {
        repository?.let { loadedRepository ->
            mutableStatistics.value = loadedRepository.statistics()
            mutableRecentSessions.value = loadedRepository.recentSessions()
        }
    }

    private fun publish(nextState: PomodoroTimerState): PomodoroTimerState {
        mutableState.value = nextState
        return nextState
    }
}
