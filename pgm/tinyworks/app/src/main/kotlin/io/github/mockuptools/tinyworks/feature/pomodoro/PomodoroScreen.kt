package io.github.mockuptools.tinyworks.feature.pomodoro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

private enum class PendingTimerAction {
    START,
    RESUME,
    START_BREAK,
    START_NEXT_WORK,
}

private enum class PomodoroTab {
    TIMER,
    STATISTICS,
}

@Composable
fun PomodoroScreen(onNavigateHome: () -> Unit) {
    val context = LocalContext.current
    val state by PomodoroSession.state
    val statistics by PomodoroSession.statistics
    val recentSessions by PomodoroSession.recentSessions
    var selectedTab by rememberSaveable { mutableStateOf(PomodoroTab.TIMER) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingTimerAction?>(null) }

    fun returnHomeForMissingNotificationPermission() {
        PomodoroSession.reset()
        PomodoroTimerServiceController.stop(context)
        Toast.makeText(
            context,
            "通知権限がないためホームに戻ります",
            Toast.LENGTH_LONG,
        ).show()
        onNavigateHome()
    }

    fun executeAction(action: PendingTimerAction) {
        when (action) {
            PendingTimerAction.START -> PomodoroSession.start()
            PendingTimerAction.RESUME -> PomodoroSession.resume()
            PendingTimerAction.START_BREAK -> PomodoroSession.startBreak()
            PendingTimerAction.START_NEXT_WORK -> PomodoroSession.startNextWork()
        }
        PomodoroTimerServiceController.start(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            val action = pendingAction
            pendingAction = null
            if (!granted) {
                returnHomeForMissingNotificationPermission()
            } else if (action != null) {
                executeAction(action)
            }
        },
    )

    fun requestOrExecute(action: PendingTimerAction) {
        if (hasNotificationPermission(context)) {
            executeAction(action)
        } else {
            pendingAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasNotificationPermission(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showSettings) {
        PomodoroSettingsScreen(
            initialDurations = PomodoroSession.currentDurations,
            onSave = { durations ->
                PomodoroSession.updateDurations(durations)
                showSettings = false
            },
            onCancel = { showSettings = false },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 24.dp),
        ) {
            Text("Pomodoro", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { showSettings = true },
                enabled = state.status == PomodoroStatus.READY && selectedTab == PomodoroTab.TIMER,
            ) {
                Text("⚙", style = MaterialTheme.typography.titleLarge)
            }
        }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == PomodoroTab.TIMER,
                onClick = { selectedTab = PomodoroTab.TIMER },
                text = { Text("タイマー") },
            )
            Tab(
                selected = selectedTab == PomodoroTab.STATISTICS,
                onClick = { selectedTab = PomodoroTab.STATISTICS },
                text = { Text("統計・履歴") },
            )
        }

        when (selectedTab) {
            PomodoroTab.TIMER -> TimerTab(
                state = state,
                onStart = { requestOrExecute(PendingTimerAction.START) },
                onPause = {
                    PomodoroSession.pause()
                    PomodoroTimerServiceController.stop(context)
                },
                onResume = { requestOrExecute(PendingTimerAction.RESUME) },
                onStartBreak = { requestOrExecute(PendingTimerAction.START_BREAK) },
                onStartNextWork = { requestOrExecute(PendingTimerAction.START_NEXT_WORK) },
                onReset = {
                    PomodoroSession.reset()
                    PomodoroTimerServiceController.stop(context)
                },
                onNavigateHome = onNavigateHome,
            )

            PomodoroTab.STATISTICS -> PomodoroStatisticsScreen(
                statistics = statistics,
                sessions = recentSessions,
                onDeleteStatistics = PomodoroSession::deleteStatistics,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimerTab(
    state: PomodoroTimerState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStartBreak: () -> Unit,
    onStartNextWork: () -> Unit,
    onReset: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (state.phase == PomodoroPhase.WORK) "作業" else "休憩")
        Text(
            text = if (state.overtimeMillis > 0L) {
                "+${formatDuration(state.overtimeMillis)}"
            } else {
                formatDuration(state.remainingMillis)
            },
            style = MaterialTheme.typography.displayMedium,
        )
        Text(statusText(state))

        when (state.status) {
            PomodoroStatus.READY -> Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("作業を開始")
            }

            PomodoroStatus.RUNNING -> {
                Button(
                    onClick = onPause,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("一時停止")
                }
                if (state.phase == PomodoroPhase.WORK) {
                    Button(
                        onClick = onStartBreak,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("休憩を開始")
                    }
                }
            }

            PomodoroStatus.PAUSED -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("再開")
                }
                if (state.phase == PomodoroPhase.WORK) {
                    Button(
                        onClick = onStartBreak,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("休憩を開始")
                    }
                }
            }

            PomodoroStatus.COMPLETED -> Button(
                onClick = onStartNextWork,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("次の作業を開始")
            }
        }

        if (state.status != PomodoroStatus.READY) {
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("リセット")
            }
        }

        Button(
            onClick = onNavigateHome,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("ホームへ戻る")
        }
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun statusText(state: PomodoroTimerState): String = when {
    state.overtimeMillis > 0L -> "作業時間を超過しています"
    state.status == PomodoroStatus.COMPLETED -> "休憩が終了しました"
    state.status == PomodoroStatus.PAUSED -> "一時停止中"
    state.status == PomodoroStatus.RUNNING -> "計測中"
    else -> "待機中"
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
