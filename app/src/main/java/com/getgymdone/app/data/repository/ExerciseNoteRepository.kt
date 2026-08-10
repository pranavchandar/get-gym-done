package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.ExerciseNoteDao
import com.getgymdone.app.data.db.entities.ExerciseNote
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Per-exercise notes. Notes are local-only (never uploaded — the Friends layer publishes show-up
 * metrics only) and survive reseeds because they live in their own table.
 */
@Singleton
class ExerciseNoteRepository @Inject constructor(
    private val dao: ExerciseNoteDao,
) {
    /** The note text for [exerciseId], empty string when there is none. */
    fun observe(exerciseId: String): Flow<String> = dao.observe(exerciseId).map { it?.text.orEmpty() }

    /**
     * Persist [text] for [exerciseId]. Blank text deletes the row rather than storing an empty
     * string, so "no note" has exactly one representation.
     */
    suspend fun save(exerciseId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            dao.deleteById(exerciseId)
        } else {
            dao.upsert(
                ExerciseNote(
                    exerciseId = exerciseId,
                    text = trimmed,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
