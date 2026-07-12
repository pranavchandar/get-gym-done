package com.getgymdone.app.ui

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot signal that a workout was just finished, so the Today screen can play a confetti
 * celebration the next time it appears. App-scoped because the workout-complete screen and Home
 * are separate navigation destinations with independent ViewModels: the completer [arm]s it, Home
 * reads [pending] once on arrival and [consume]s it so it never replays.
 */
@Singleton
class WorkoutCelebrationSignal @Inject constructor() {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun arm() { _pending.value = true }
    fun consume() { _pending.value = false }
}
