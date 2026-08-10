package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.ExerciseNote
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseNoteDao {
    /** Emits null while the exercise has no note (a blank note is deleted, never stored empty). */
    @Query("SELECT * FROM exercise_note WHERE exerciseId = :exerciseId")
    fun observe(exerciseId: String): Flow<ExerciseNote?>

    @Query("SELECT * FROM exercise_note")
    suspend fun getAll(): List<ExerciseNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: ExerciseNote)

    @Query("DELETE FROM exercise_note WHERE exerciseId = :exerciseId")
    suspend fun deleteById(exerciseId: String)
}
