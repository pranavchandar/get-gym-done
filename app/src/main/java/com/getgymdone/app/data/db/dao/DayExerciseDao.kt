package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.getgymdone.app.data.db.entities.DayExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface DayExerciseDao {
    @Query("SELECT * FROM day_exercise WHERE workoutDayId = :workoutDayId ORDER BY orderIndex")
    suspend fun getByDay(workoutDayId: String): List<DayExercise>

    @Query("SELECT * FROM day_exercise")
    suspend fun getAll(): List<DayExercise>

    @Query("SELECT * FROM day_exercise")
    fun observeAll(): Flow<List<DayExercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DayExercise>)

    @Query("DELETE FROM day_exercise WHERE workoutDayId = :workoutDayId")
    suspend fun deleteByDay(workoutDayId: String)

    /** Atomically swap a day's exercise list — clears the old rows, inserts the new ordered set. */
    @Transaction
    suspend fun replaceForDay(workoutDayId: String, items: List<DayExercise>) {
        deleteByDay(workoutDayId)
        upsertAll(items)
    }
}
