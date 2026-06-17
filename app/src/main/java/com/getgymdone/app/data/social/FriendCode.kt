package com.getgymdone.app.data.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Payload encoded in a user's friend QR code. Scanning it adds the encoded user as a friend.
 * Encoded as `ggd1:<json>` so the scanner can reject QR codes that aren't ours.
 */
@Serializable
data class FriendCode(
    val userId: String,
    val handle: String,
    val color: String,
) {
    fun encode(): String = PREFIX + Json.encodeToString(this)

    companion object {
        private const val PREFIX = "ggd1:"

        /** Parses a scanned QR string, or null if it isn't a valid Get Gym Done friend code. */
        fun decode(raw: String): FriendCode? {
            if (!raw.startsWith(PREFIX)) return null
            return runCatching { Json.decodeFromString<FriendCode>(raw.removePrefix(PREFIX)) }.getOrNull()
        }
    }
}
