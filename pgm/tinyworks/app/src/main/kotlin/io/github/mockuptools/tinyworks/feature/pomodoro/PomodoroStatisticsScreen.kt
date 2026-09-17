package io.github.mockuptools.tinyworks.feature.pomodoro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PomodoroStatisticsScreen(
    statistics: PomodoroStatistics,
    sessions: List<PomodoroSessionRecord>,
    onDeleteStatistics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("統計")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("作業セッション: ${statistics.workSessionCount}回")
                    Text("休憩完了: ${statistics.completedBreakCount}回")
                    Text("総作業時間: ${formatDuration(statistics.totalWorkMillis)}")
                    Text("総オーバータイム: ${formatDuration(statistics.totalOvertimeMillis)}")
                }
            }
        }
        item {
            Text("履歴（最新100件）")
        }
        if (sessions.isEmpty()) {
            item {
                Text("履歴はありません")
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                SessionHistoryItem(session)
            }
        }
        item {
            OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("統計を削除")
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("統計を削除しますか？") },
            text = { Text("保存されているPomodoroの履歴と統計をすべて削除します。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteStatistics()
                    },
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@Composable
private fun SessionHistoryItem(session: PomodoroSessionRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(formatDate(session.workStartedAtEpochMillis))
            Text("作業: ${formatDuration(session.workElapsedMillis)}")
            Text("オーバータイム: +${formatDuration(session.overtimeMillis)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("休憩: ${formatDuration(session.breakElapsedMillis ?: 0L)}")
                Text(if (session.breakEndedAtEpochMillis == null) "未完了" else "完了")
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT,
        Locale.JAPAN,
    ).format(Date(epochMillis))

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
