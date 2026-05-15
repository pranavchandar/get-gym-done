package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "day_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutDay::class,
            parentColumns = ["id"],
            childColumns = ["workoutDayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
        ),
    ],
    indices = [Index("workoutDayId"), Index("exerciseId")],
)
data class DayExercise(
    @PrimaryKey val id: String,
    val workoutDayId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val prescribedSets: Int,
    val prescribedRepsLow: Int,
    val prescribedRepsHigh: Int,
)
