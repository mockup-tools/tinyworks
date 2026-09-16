package io.github.mockuptools.tinyworks.feature.pomodoro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PomodoroScreen(onNavigateHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pomodoro", style = MaterialTheme.typography.headlineMedium)
        Text("Pomodoro機能の基礎画面です。詳細仕様はこれから定義します。")
        Button(
            onClick = onNavigateHome,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("ホームへ戻る")
        }
    }
}
