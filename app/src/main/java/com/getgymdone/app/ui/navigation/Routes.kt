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

    /**
     * Builder for a new user-defined split. [seedSplitId] is optional — when present, the
     * builder pre-loads that preset's day/exercise structure for editing (the "Build my own"
     * path from RoutineMethod). When null, the builder starts blank (the "Custom" entry on
     * Pick Split).
     */
    @Serializable data class CustomizeRoutine(val seedSplitId: String? = null) : Route

    // ── Main shell (bottom tabs: Today / Workouts / Profile / Settings) ──
    @Serializable data object Home : Route

    // ── Friends (opt-in social layer: QR friend-add + leaderboard) ──
    @Serializable data object Social : Route

    // ── Day & workout ─────────────────────────────────────────────
    @Serializable data class DayOverview(val workoutDayId: String) : Route
    @Serializable data class ActiveWorkout(val workoutDayId: String) : Route
    @Serializable data class WorkoutComplete(val sessionId: String) : Route

    // ── Debug (Week-2 DoD: a screen to see seeded data) ───────────
    @Serializable data object DebugData : Route
}
