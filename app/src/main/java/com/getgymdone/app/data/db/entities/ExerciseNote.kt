package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A free-text note the user wrote for an exercise (cues, machine settings, links to a form video…).
 *
 * Deliberately its own table rather than a column on [Exercise]: `SeedLoader` UPDATEs exercise rows
 * in place whenever a new seed marker ships, which would clobber anything user-written stored there.
 * One note per exercise, so the exercise id doubles as the primary key.
 */
@Serializable
@Entity(
    tableName = "exercise_note",
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
data class ExerciseNote(
    @PrimaryKey val exerciseId: String,
    val text: String,
    val updatedAt: Long,
)
