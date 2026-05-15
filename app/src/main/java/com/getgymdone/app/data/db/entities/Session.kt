package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutDay::class,
            parentColumns = ["id"],
            childColumns = ["workoutDayId"],
        )
    ],
    indices = [Index("workoutDayId")],
)
data class Session(
    @PrimaryKey val id: String,
    val workoutDayId: String,
    val startedAt: Long,
    val completedAt: Long?,
    val notes: String?,
)
