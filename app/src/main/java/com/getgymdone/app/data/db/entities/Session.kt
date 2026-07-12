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
    /** Null for a logged cardio/sport activity, which belongs to no scheduled workout day. */
    val workoutDayId: String?,
    val startedAt: Long,
    val completedAt: Long?,
    val notes: String?,
    /** Set only for non-gym activities (e.g. "Running", "Tennis"); null for workouts/rest days. */
    val activityType: String? = null,
    /** Activity duration in minutes, when logged. */
    val durationMin: Int? = null,
)
