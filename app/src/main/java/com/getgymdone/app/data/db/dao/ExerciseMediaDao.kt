package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.ExerciseMedia
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseMediaDao {
    @Query("SELECT * FROM exercise_media WHERE exerciseId = :exerciseId ORDER BY addedAt")
    fun observeForExercise(exerciseId: String): Flow<List<ExerciseMedia>>

    @Query("SELECT * FROM exercise_media WHERE id = :id")
    suspend fun getById(id: String): ExerciseMedia?

    @Query("SELECT * FROM exercise_media")
    suspend fun getAll(): List<ExerciseMedia>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: ExerciseMedia)

    @Query("DELETE FROM exercise_media WHERE id = :id")
    suspend fun deleteById(id: String)
}
