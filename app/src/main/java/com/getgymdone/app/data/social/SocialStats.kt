package com.getgymdone.app.data.social

import kotlinx.serialization.Serializable

/**
 * The minimal, derived summary published to the backend and shown on friends' leaderboards.
 *
 * Deliberately "show-up" metrics only — streaks and session counts, which are fair across body
 * sizes and experience. It must **never** carry set logs, weights, or body metrics; those stay
 * on-device. All fields default so Firestore can deserialize it via its no-arg constructor.
 */
@Serializable
data class SocialStats(
    val handle: String = "",
    val color: String = "lime",
    val photo: String = "",            // base64 JPEG thumbnail (display picture), empty = none
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    /** True when this user's streak is on the make-or-break day today — drives friends' nudges. */
    val streakAtRisk: Boolean = false,
    val weekSessions: Int = 0,
    val weekTarget: Int = 0,
    val totalSessions: Int = 0,
    val lastActiveAt: Long = 0L,
    val updatedAt: Long = 0L,
)
