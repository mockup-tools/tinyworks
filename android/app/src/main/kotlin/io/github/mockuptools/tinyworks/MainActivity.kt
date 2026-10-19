package io.github.mockuptools.tinyworks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.mockuptools.tinyworks.core.TinyworksTheme
import io.github.mockuptools.tinyworks.core.ui.TinyworksAppShell
import io.github.mockuptools.tinyworks.core.ui.TinyworksMenuItem
import io.github.mockuptools.tinyworks.feature.home.HomeScreen
import io.github.mockuptools.tinyworks.feature.pomodoro.PomodoroScreen
import io.github.mockuptools.tinyworks.feature.pomodoro.PomodoroSession
import io.github.mockuptools.tinyworks.feature.poyday.PoyDayScreen

class MainActivity : ComponentActivity() {
    private var openPomodoroRequestId by mutableIntStateOf(0)
    private var openPoyDayRequestId by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PomodoroSession.initialize(applicationContext)
        if (intent.getBooleanExtra(EXTRA_OPEN_POMODORO, false)) {
            openPomodoroRequestId = 1
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_POYDAY, false)) {
            openPoyDayRequestId = 1
        }
        setContent {
            TinyworksTheme {
                TinyworksApp(openPomodoroRequestId, openPoyDayRequestId)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_POMODORO, false)) {
            openPomodoroRequestId++
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_POYDAY, false)) {
            openPoyDayRequestId++
        }
    }

    companion object {
        const val EXTRA_OPEN_POMODORO =
            "io.github.mockuptools.tinyworks.extra.OPEN_POMODORO"
        const val EXTRA_OPEN_POYDAY =
            "io.github.mockuptools.tinyworks.extra.OPEN_POYDAY"
    }
}

private enum class Destination {
    HOME,
    POMODORO,
    POYDAY,
}

@Composable
private fun TinyworksApp(
    openPomodoroRequestId: Int,
    openPoyDayRequestId: Int,
) {
    var destination by rememberSaveable {
        mutableStateOf(
            when {
                openPoyDayRequestId > 0 -> Destination.POYDAY
                openPomodoroRequestId > 0 -> Destination.POMODORO
                else -> Destination.HOME
            },
        )
    }

    LaunchedEffect(openPomodoroRequestId) {
        if (openPomodoroRequestId > 0) {
            destination = Destination.POMODORO
        }
    }

    LaunchedEffect(openPoyDayRequestId) {
        if (openPoyDayRequestId > 0) {
            destination = Destination.POYDAY
        }
    }

    TinyworksAppShell(
        menuItems = listOf(
            TinyworksMenuItem(
                label = "ホーム",
                selected = destination == Destination.HOME,
                onClick = { destination = Destination.HOME },
            ),
            TinyworksMenuItem(
                label = "Pomodoro",
                selected = destination == Destination.POMODORO,
                onClick = { destination = Destination.POMODORO },
            ),
            TinyworksMenuItem(
                label = "ポイの日",
                selected = destination == Destination.POYDAY,
                onClick = { destination = Destination.POYDAY },
            ),
        ),
    ) { contentModifier ->
        when (destination) {
            Destination.HOME -> HomeScreen(
                modifier = contentModifier,
                onOpenPomodoro = { destination = Destination.POMODORO },
                onOpenPoyDay = { destination = Destination.POYDAY },
            )

            Destination.POMODORO -> PomodoroScreen(
                modifier = contentModifier,
                onNavigateHome = { destination = Destination.HOME },
            )

            Destination.POYDAY -> PoyDayScreen(
                modifier = contentModifier,
                onNavigateHome = { destination = Destination.HOME },
            )
        }
    }
}
