package io.github.mockuptools.tinyworks.feature.pomodoro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun PomodoroSettingsScreen(
    modifier: Modifier = Modifier,
    initialDurations: PomodoroDurations,
    onSave: (PomodoroDurations) -> Unit,
    onCancel: () -> Unit,
) {
    var workMinutes by rememberSaveable(initialDurations.workMillis) {
        mutableStateOf(initialDurations.workMinutes.toString())
    }
    var workSeconds by rememberSaveable(initialDurations.workMillis) {
        mutableStateOf(initialDurations.workSeconds.toString())
    }
    var breakMinutes by rememberSaveable(initialDurations.breakMillis) {
        mutableStateOf(initialDurations.breakMinutes.toString())
    }
    var breakSeconds by rememberSaveable(initialDurations.breakMillis) {
        mutableStateOf(initialDurations.breakSeconds.toString())
    }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun save() {
        val workMillis = parseDuration(workMinutes, workSeconds)
        val breakMillis = parseDuration(breakMinutes, breakSeconds)
        validationMessage = when {
            workMillis == null -> "作業時間は1秒以上、秒は0〜59で入力してください"
            breakMillis == null -> "休憩時間は1秒以上、秒は0〜59で入力してください"
            else -> null
        }
        if (workMillis != null && breakMillis != null) {
            onSave(PomodoroDurations(workMillis, breakMillis))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("タイマー設定", style = MaterialTheme.typography.headlineMedium)
        Text("時間を分と秒で入力してください。")

        DurationInputRow(
            label = "作業時間",
            minutes = workMinutes,
            seconds = workSeconds,
            onMinutesChange = { workMinutes = it.onlyDigits(maxLength = 3) },
            onSecondsChange = { workSeconds = it.onlyDigits(maxLength = 2) },
        )
        DurationInputRow(
            label = "休憩時間",
            minutes = breakMinutes,
            seconds = breakSeconds,
            onMinutesChange = { breakMinutes = it.onlyDigits(maxLength = 3) },
            onSecondsChange = { breakSeconds = it.onlyDigits(maxLength = 2) },
        )

        validationMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ::save) {
                Text("保存")
            }
            TextButton(onClick = onCancel) {
                Text("キャンセル")
            }
        }
    }
}

@Composable
private fun DurationInputRow(
    label: String,
    minutes: String,
    seconds: String,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedTextField(
                value = minutes,
                onValueChange = onMinutesChange,
                modifier = Modifier.weight(1f),
                label = { Text("分") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = seconds,
                onValueChange = onSecondsChange,
                modifier = Modifier.weight(1f),
                label = { Text("秒") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

private fun parseDuration(minutesText: String, secondsText: String): Long? {
    val minutes = minutesText.toLongOrNull() ?: return null
    val seconds = secondsText.toLongOrNull() ?: return null
    if (minutes < 0L || seconds !in 0L..59L) return null
    val durationMillis = minutes * 60_000L + seconds * 1_000L
    return durationMillis.takeIf { it > 0L }
}

private fun String.onlyDigits(maxLength: Int): String =
    filter(Char::isDigit).take(maxLength)
