package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "user_prefs")
data class UserPrefs(
    @PrimaryKey val id: Int = 0,           // singleton row
    val activeSplitId: String? = null,
    val units: String = "kg",              // "kg" | "lbs"
    val theme: String = "system",          // "light" | "dark" | "system"
    val onboardingComplete: Boolean = false,
    val restSeconds: Int = 90,             // rest-timer duration, remembered across workouts
    val accent: String = "lime",           // accent palette key (see AccentPalette)
    val socialEnabled: Boolean = false,    // opt-in to the Friends layer (off = fully local, no network)
    val socialUserId: String? = null,      // Firebase anonymous uid, assigned on opt-in
    val socialHandle: String? = null,      // profile display name (shown on Profile + to friends)
    val socialColor: String? = null,       // avatar accent key (fallback when no photo is set)
    val avatarPhoto: String? = null,       // display picture: base64 JPEG thumbnail, null = none
)
