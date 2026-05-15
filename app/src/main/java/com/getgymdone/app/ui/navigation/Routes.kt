package com.getgymdone.app.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose destinations. One sealed surface for the whole app,
 * grouped by section per the handover's 17-destination IA.
 */
sealed interface Route {

    // ── Onboarding ────────────────────────────────────────────────
    @Serializable data object Splash : Route
    @Serializable data object PickSplit : Route
    @Serializable data class RoutineMethod(val splitId: String) : Route
    @Serializable data class CustomizeRoutine(val splitId: String) : Route

    // ── Main shell (bottom tabs) ──────────────────────────────────
    @Serializable data object Home : Route
    @Serializable data object WorkoutsList : Route
    @Serializable data object Profile : Route
    @Serializable data object Settings : Route

    // ── Day & workout ─────────────────────────────────────────────
    @Serializable data class DayOverview(val workoutDayId: String) : Route
    @Serializable data class ActiveWorkout(val workoutDayId: String) : Route
    @Serializable data class WorkoutComplete(val sessionId: String) : Route

    // ── Debug (Week-2 DoD: a screen to see seeded data) ───────────
    @Serializable data object DebugData : Route
}
