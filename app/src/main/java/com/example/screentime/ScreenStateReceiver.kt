package com.screentime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, ScreenTimeService::class.java)

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                // Screen turned on, resume the timer if it was running before
                if (TimerManager.timerState.value == TimerState.PAUSED) {
                    serviceIntent.action = "RESUME"
                    context.startService(serviceIntent)
                }
            }
            Intent.ACTION_SCREEN_OFF -> {
                // Screen turned off, pause the timer
                if (TimerManager.timerState.value == TimerState.RUNNING) {
                    serviceIntent.action = "PAUSE"
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
