package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.DayExercise

@Dao
interface DayExerciseDao {
    @Query("SELECT * FROM day_exercise WHERE workoutDayId = :workoutDayId ORDER BY orderIndex")
    suspend fun getByDay(workoutDayId: String): List<DayExercise>

    @Query("SELECT * FROM day_exercise")
    suspend fun getAll(): List<DayExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DayExercise>)
}
