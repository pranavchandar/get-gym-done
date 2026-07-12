package com.getgymdone.app.domain

import com.getgymdone.app.data.db.entities.SetLog
import kotlin.math.abs

/** A progressive-overload nudge: the weight you've stalled at and the next load to try (both kg). */
data class WeightSuggestion(
    val currentWeightKg: Double,
    val suggestedWeightKg: Double,
    val reps: Int,
)

/**
 * Double-progression nudge. Across the most recent [sessions] completed sessions for one exercise, if
 * a set position used the *same* weight every time while hitting reps at or above the top of the
 * prescribed range ([repsHigh]), this returns that weight plus [incrementKg]. It picks the heaviest
 * qualifying set so the nudge tracks the top working weight, and reports the lowest reps actually hit
 * so the message stays truthful. Returns null when there's too little history or nothing has stalled.
 *
 * [history] is every completed set for a single exercise, in any order — only the session grouping
 * and timestamps are used.
 */
fun weightIncreaseSuggestion(
    history: List<SetLog>,
    repsHigh: Int,
    incrementKg: Double,
    sessions: Int = 2,
): WeightSuggestion? {
    if (sessions < 2 || history.isEmpty()) return null
    val target = repsHigh.coerceAtLeast(1)

    // The most recent [sessions] sessions, ordered by each session's latest set time.
    val recent = history.groupBy { it.sessionId }
        .values
        .sortedByDescending { sets -> sets.maxOf { it.completedAt } }
        .take(sessions)
    if (recent.size < sessions) return null

    // Collect each set position's logs across those sessions.
    val bySetNumber = HashMap<Int, MutableList<SetLog>>()
    recent.forEach { sets ->
        sets.forEach { bySetNumber.getOrPut(it.setNumber) { mutableListOf() }.add(it) }
    }

    var bestWeight: Double? = null
    var bestReps = 0
    bySetNumber.values.forEach { logs ->
        if (logs.size < sessions) return@forEach // not performed in every session
        val weight = logs.first().weightKg
        val sameWeight = logs.all { abs(it.weightKg - weight) < 1e-3 }
        val hitTopReps = logs.all { it.reps >= target }
        if (sameWeight && hitTopReps && (bestWeight == null || weight > bestWeight!!)) {
            bestWeight = weight
            bestReps = logs.minOf { it.reps }
        }
    }
    return bestWeight?.let { WeightSuggestion(it, it + incrementKg, bestReps) }
}
