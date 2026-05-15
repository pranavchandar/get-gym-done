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
)
