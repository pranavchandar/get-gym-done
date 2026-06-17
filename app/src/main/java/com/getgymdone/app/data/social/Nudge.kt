package com.getgymdone.app.data.social

import kotlinx.serialization.Serializable

/**
 * A one-shot poke from a friend, written to the recipient's `users/{uid}/nudges` subcollection.
 * The recipient's app surfaces it as a local notification on its next sync, then deletes it.
 * All fields default so Firestore can deserialize via its no-arg constructor.
 */
@Serializable
data class Nudge(
    val fromUserId: String = "",
    val fromHandle: String = "",
    val streakDays: Int = 0,
    val createdAt: Long = 0L,
)
