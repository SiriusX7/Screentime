package com.screentime

import kotlinx.coroutines.flow.MutableStateFlow

enum class TimerState {
    IDLE, RUNNING, PAUSED
}

object TimerManager {
    val timerState = MutableStateFlow(TimerState.IDLE)
    val timeRemaining = MutableStateFlow(0L) // milliseconds
    val totalTime = MutableStateFlow(0L) // milliseconds
}
