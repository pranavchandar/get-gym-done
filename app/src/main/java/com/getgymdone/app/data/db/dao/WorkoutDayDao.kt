package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.WorkoutDay
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDayDao {
    @Query("SELECT * FROM workout_day WHERE splitId = :splitId ORDER BY dayNumber")
    fun observeBySplit(splitId: String): Flow<List<WorkoutDay>>

    @Query("SELECT * FROM workout_day WHERE splitId = :splitId ORDER BY dayNumber")
    suspend fun getBySplit(splitId: String): List<WorkoutDay>

    @Query("SELECT * FROM workout_day")
    suspend fun getAll(): List<WorkoutDay>

    @Query("SELECT * FROM workout_day")
    fun observeAll(): Flow<List<WorkoutDay>>

    @Query("SELECT * FROM workout_day WHERE id = :id")
    suspend fun getById(id: String): WorkoutDay?

    @Query("SELECT * FROM workout_day WHERE splitId = :splitId AND dayNumber = :dayNumber")
    suspend fun getByDayNumber(splitId: String, dayNumber: Int): WorkoutDay?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WorkoutDay>)

    /** In-place renumber so we don't REPLACE the row (which would CASCADE-wipe its day_exercise). */
    @Query("UPDATE workout_day SET dayNumber = :dayNumber WHERE id = :id")
    suspend fun setDayNumber(id: String, dayNumber: Int)

    @Query("UPDATE workout_day SET name = :name WHERE id = :id")
    suspend fun setName(id: String, name: String)

    @Query("DELETE FROM workout_day WHERE id = :id")
    suspend fun deleteById(id: String)
}
