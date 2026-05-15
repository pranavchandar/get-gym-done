package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "workout_day",
    foreignKeys = [
        ForeignKey(
            entity = Split::class,
            parentColumns = ["id"],
            childColumns = ["splitId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("splitId")],
)
data class WorkoutDay(
    @PrimaryKey val id: String,
    val splitId: String,
    val dayNumber: Int,
    val name: String,
    val muscleGroups: List<String>,
)
