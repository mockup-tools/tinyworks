package io.github.mockuptools.tinyworks.feature.pomodoro;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PomodoroTimerTest {
    @Test
    public void initialStateIsReadyForWork() {
        PomodoroTimerState state = new PomodoroTimer().snapshot();

        assertEquals(PomodoroPhase.WORK, state.getPhase());
        assertEquals(PomodoroStatus.READY, state.getStatus());
        assertEquals(PomodoroTimerKt.WORK_DURATION_MILLIS, state.getRemainingMillis());
    }

    @Test
    public void startPauseAndResumePreserveElapsedTime() {
        PomodoroTimer timer = new PomodoroTimer();

        timer.start(1_000L);
        timer.advance(11_000L);
        PomodoroTimerState paused = timer.pause(11_000L);

        assertEquals(PomodoroStatus.PAUSED, paused.getStatus());
        assertEquals(10_000L, paused.getElapsedMillis());

        timer.resume(20_000L);
        PomodoroTimerState resumed = timer.advance(25_000L);

        assertEquals(PomodoroStatus.RUNNING, resumed.getStatus());
        assertEquals(15_000L, resumed.getElapsedMillis());
    }

    @Test
    public void workContinuesAsOvertimeAfterDuration() {
        PomodoroTimer timer = new PomodoroTimer();

        timer.start(0L);
        PomodoroTimerState state = timer.advance(PomodoroTimerKt.WORK_DURATION_MILLIS + 12_000L);

        assertEquals(PomodoroStatus.RUNNING, state.getStatus());
        assertEquals(0L, state.getRemainingMillis());
        assertEquals(12_000L, state.getOvertimeMillis());
    }

    @Test
    public void startingBreakResetsElapsedTimeAndStartsBreak() {
        PomodoroTimer timer = new PomodoroTimer();

        timer.start(0L);
        timer.advance(PomodoroTimerKt.WORK_DURATION_MILLIS + 12_000L);
        PomodoroTimerState state = timer.startBreak(PomodoroTimerKt.WORK_DURATION_MILLIS + 12_000L);

        assertEquals(PomodoroPhase.BREAK, state.getPhase());
        assertEquals(PomodoroStatus.RUNNING, state.getStatus());
        assertEquals(0L, state.getElapsedMillis());
        assertEquals(PomodoroTimerKt.BREAK_DURATION_MILLIS, state.getRemainingMillis());
    }

    @Test
    public void breakCompletesWithoutStartingNextWorkAutomatically() {
        PomodoroTimer timer = new PomodoroTimer();

        timer.start(0L);
        timer.startBreak(1_000L);
        PomodoroTimerState state = timer.advance(1_000L + PomodoroTimerKt.BREAK_DURATION_MILLIS);

        assertEquals(PomodoroPhase.BREAK, state.getPhase());
        assertEquals(PomodoroStatus.COMPLETED, state.getStatus());
        assertEquals(0L, state.getRemainingMillis());

        PomodoroTimerState nextWork = timer.startNextWork(10_000L);
        assertEquals(PomodoroPhase.WORK, nextWork.getPhase());
        assertEquals(PomodoroStatus.RUNNING, nextWork.getStatus());
    }

    @Test
    public void resetReturnsToInitialState() {
        PomodoroTimer timer = new PomodoroTimer();

        timer.start(0L);
        timer.advance(30_000L);
        PomodoroTimerState state = timer.reset();

        assertEquals(PomodoroPhase.WORK, state.getPhase());
        assertEquals(PomodoroStatus.READY, state.getStatus());
        assertEquals(0L, state.getElapsedMillis());
        assertEquals(0L, state.getOvertimeMillis());
    }

    @Test
    public void timeDoesNotMoveWhenNotRunning() {
        PomodoroTimer timer = new PomodoroTimer();

        PomodoroTimerState initial = timer.snapshot();
        PomodoroTimerState advanced = timer.advance(60_000L);

        assertEquals(initial, advanced);
    }
}
