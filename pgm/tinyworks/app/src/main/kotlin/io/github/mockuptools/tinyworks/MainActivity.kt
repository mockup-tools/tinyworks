package io.github.mockuptools.tinyworks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.mockuptools.tinyworks.core.TinyworksTheme
import io.github.mockuptools.tinyworks.feature.home.HomeScreen
import io.github.mockuptools.tinyworks.feature.pomodoro.PomodoroScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TinyworksTheme {
                TinyworksApp()
            }
        }
    }
}

private enum class Destination {
    HOME,
    POMODORO,
}

@Composable
private fun TinyworksApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }

    when (destination) {
        Destination.HOME -> HomeScreen(
            onOpenPomodoro = { destination = Destination.POMODORO },
        )

        Destination.POMODORO -> PomodoroScreen(
            onNavigateHome = { destination = Destination.HOME },
        )
    }
}
