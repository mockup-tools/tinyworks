package io.github.mockuptools.tinyworks.feature.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.mockuptools.tinyworks.MainActivity
import java.util.Locale

private const val NOTIFICATION_CHANNEL_ID = "pomodoro_timer"
private const val NOTIFICATION_ID = 1001
private const val NOTIFICATION_REFRESH_MILLIS = 1_000L

object PomodoroTimerServiceController {
    fun start(context: Context) {
        val serviceIntent = Intent(context, PomodoroTimerService::class.java)
        context.startForegroundService(serviceIntent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, PomodoroTimerService::class.java))
    }
}

class PomodoroTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val state = PomodoroSession.advance()
            if (!state.isRunning) {
                stopSelf()
                return
            }

            updateNotification(state)
            handler.postDelayed(this, NOTIFICATION_REFRESH_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = PomodoroSession.advance()
        if (!state.isRunning) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(state),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, NOTIFICATION_REFRESH_MILLIS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Pomodoroタイマー",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Pomodoroタイマーの進行状況"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: PomodoroTimerState): Notification {
        val openPomodoroIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_POMODORO, true)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openPomodoroIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Tinyworks Pomodoro")
            .setContentText(notificationText(state))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(state: PomodoroTimerState) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(state),
        )
    }

    private fun notificationText(state: PomodoroTimerState): String = when (state.phase) {
        PomodoroPhase.WORK -> if (state.overtimeMillis > 0L) {
            "作業中 +${formatMillis(state.overtimeMillis)}"
        } else {
            "作業中 残り ${formatMillis(state.remainingMillis)}"
        }

        PomodoroPhase.BREAK -> "休憩中 残り ${formatMillis(state.remainingMillis)}"
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
