package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    // Filename only, not a resource ID — lets us swap PNGs without a DB migration.
    val illustrationFilename: String,
    val formCues: List<String>,
    val equipment: String,
    val defaultSets: Int,
    val defaultRepsLow: Int,
    val defaultRepsHigh: Int,
)
