package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A friend added by scanning their QR code. The friend graph lives entirely on-device — the
 * backend only ever stores each user's *own* stats doc, so there is no server-side friend list.
 * The friend's latest synced stats are cached on the row so the leaderboard renders offline;
 * they're refreshed on each sync. [userId] is the friend's Firebase anonymous uid.
 */
@Serializable
@Entity(tableName = "friend")
data class Friend(
    @PrimaryKey val userId: String,
    val handle: String,
    val color: String,                 // avatar accent key (see AccentPalette)
    val addedAt: Long,
    // Cached synced stats — all zero/null until the first successful pull.
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakAtRisk: Boolean = false,
    val weekSessions: Int = 0,
    val weekTarget: Int = 0,
    val totalSessions: Int = 0,
    val lastActiveAt: Long? = null,
    val statsUpdatedAt: Long? = null,
    val photo: String? = null,         // cached base64 JPEG thumbnail (their display picture)
)
