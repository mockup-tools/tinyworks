package io.github.mockuptools.tinyworks.feature.home

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
fun HomeScreen(onOpenPomodoro: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tinyworks", style = MaterialTheme.typography.headlineMedium)
        Text("個人用の小さな機能をまとめたアプリです。")
        Button(
            onClick = onOpenPomodoro,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pomodoro")
        }
    }
}
