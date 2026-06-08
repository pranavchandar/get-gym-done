package com.getgymdone.app.domain

import com.getgymdone.app.data.db.entities.WorkoutDay

/**
 * "What workout day should I show on Home?"
 *
 * Rule (from the handover):
 *  - If a session is in progress, show that day (handled by caller).
 *  - Otherwise the next day is (lastDayNumber % dayCount) + 1.
 *  - If no sessions exist yet, default to 1.
 */
fun nextDayNumber(lastDayNumber: Int?, dayCount: Int): Int {
    if (dayCount <= 0) return 1
    if (lastDayNumber == null) return 1
    return (lastDayNumber % dayCount) + 1
}

/**
 * Resolve the next *training* day, skipping rest days. The rotation is completion-based:
 * rest days are never "completed" (they spawn no session), so advancing the plain day number
 * would stall on a rest day forever. We instead walk forward from the last completed day,
 * wrapping around, and return the first non-rest day. Falls back to the first training day
 * (or null if the split is rest-only).
 */
fun nextWorkoutDay(days: List<WorkoutDay>, lastCompletedDayNumber: Int?): WorkoutDay? {
    val trainable = days.filter { !it.isRestDay }
    if (trainable.isEmpty()) return null
    val maxNumber = days.maxOfOrNull { it.dayNumber } ?: return trainable.minByOrNull { it.dayNumber }
    if (lastCompletedDayNumber == null) return trainable.minByOrNull { it.dayNumber }

    var n: Int = lastCompletedDayNumber
    repeat(maxNumber) {
        n = (n % maxNumber) + 1
        val day = days.firstOrNull { it.dayNumber == n }
        if (day != null && !day.isRestDay) return day
    }
    return trainable.minByOrNull { it.dayNumber }
}
