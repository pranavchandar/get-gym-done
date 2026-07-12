package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY name")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: String): Exercise?

    @Query("SELECT * FROM exercise WHERE primaryMuscle = :muscle ORDER BY name")
    suspend fun getByMuscle(muscle: String): List<Exercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Exercise>)

    /**
     * Refresh a catalog exercise's descriptive fields in place (no REPLACE, so day_exercise rows
     * referencing it stay intact). Used to backfill form cues / muscles added to the seed later.
     */
    @Query(
        "UPDATE exercise SET name = :name, primaryMuscle = :primaryMuscle, " +
            "secondaryMuscles = :secondaryMuscles, formCues = :formCues, equipment = :equipment, " +
            "defaultSets = :defaultSets, defaultRepsLow = :defaultRepsLow, defaultRepsHigh = :defaultRepsHigh " +
            "WHERE id = :id",
    )
    suspend fun updateDetails(
        id: String,
        name: String,
        primaryMuscle: String,
        secondaryMuscles: List<String>,
        formCues: List<String>,
        equipment: String,
        defaultSets: Int,
        defaultRepsLow: Int,
        defaultRepsHigh: Int,
    )

    @Query("DELETE FROM exercise")
    suspend fun clear()
}
