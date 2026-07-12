package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-uploaded image or GIF attached to an exercise. Files live in app-internal storage
 * (never uploaded anywhere); [filePath] is the absolute on-device path.
 */
@Entity(
    tableName = "exercise_media",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class ExerciseMedia(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val filePath: String,
    val addedAt: Long,
)
