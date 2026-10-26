package com.screentime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenTimeService : Service() {
    private val channelId = "screen_time_channel"
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var screenStateReceiver: ScreenStateReceiver? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "Screen Time Timer", 
                NotificationManager.IMPORTANCE_DEFAULT // Changed to DEFAULT for visibility
            ).apply {
                description = "Shows the remaining screen time"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        // Register the broadcast receiver dynamically
        screenStateReceiver = ScreenStateReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val timeMs = intent.getLongExtra("TIME_MS", 0L)
                TimerManager.timerState.value = TimerState.RUNNING
                TimerManager.totalTime.value = timeMs
                TimerManager.timeRemaining.value = timeMs
                startForegroundService(timeMs)
                startTimer()
            }
            "PAUSE" -> {
                TimerManager.timerState.value = TimerState.PAUSED
                timerJob?.cancel()
                updateNotification()
            }
            "RESUME" -> {
                TimerManager.timerState.value = TimerState.RUNNING
                startTimer()
                updateNotification()
            }
            "STOP" -> {
                TimerManager.timerState.value = TimerState.IDLE
                timerJob?.cancel()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                getSystemService(NotificationManager::class.java).cancel(1)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (TimerManager.timeRemaining.value > 0 && TimerManager.timerState.value == TimerState.RUNNING) {
                delay(1000)
                TimerManager.timeRemaining.value -= 1000
                updateNotification()
            }
            if (TimerManager.timeRemaining.value <= 0) {
                TimerManager.timerState.value = TimerState.IDLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                getSystemService(NotificationManager::class.java).cancel(1)
                stopSelf()
            }
        }
    }

    private fun startForegroundService(timeMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(1, buildNotification(timeMs), type)
        } else {
            startForeground(1, buildNotification(timeMs))
        }
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(1, buildNotification(TimerManager.timeRemaining.value), type)
        } else {
            startForeground(1, buildNotification(TimerManager.timeRemaining.value))
        }
    }

    private fun buildNotification(remainingMs: Long): Notification {
        val isRunning = TimerManager.timerState.value == TimerState.RUNNING
        
        val stopIntent = Intent(this, ScreenTimeService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Calculate exact time strings
        val hours = (remainingMs / (1000 * 60 * 60))
        val minutes = (remainingMs / (1000 * 60)) % 60
        val seconds = (remainingMs / 1000) % 60
        val timeString = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_timer_24)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setColor(Color.parseColor("#FFD700"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle("Screen Time")
            .setContentText(timeString)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_stop_24, "Stop", stopPendingIntent)
            // Use "stopwatch" category to trigger the Live Status Pill on Android 12+
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH) 
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isRunning) {
            val systemTargetTime = System.currentTimeMillis() + remainingMs
            builder.setWhen(systemTargetTime)
            builder.setUsesChronometer(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true)
            }
        } else {
            builder.setUsesChronometer(false)
        }

        // Force immediate display on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        screenStateReceiver?.let {
            unregisterReceiver(it)
        }
    }
}
