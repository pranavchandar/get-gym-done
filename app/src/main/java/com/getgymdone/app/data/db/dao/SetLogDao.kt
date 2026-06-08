package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.SetLog

@Dao
interface SetLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setLog: SetLog)

    @Query("SELECT * FROM set_log WHERE sessionId = :sessionId ORDER BY setNumber")
    suspend fun getBySession(sessionId: String): List<SetLog>

    @Query("""
        SELECT * FROM set_log
         WHERE exerciseId = :exerciseId
         ORDER BY completedAt ASC
    """)
    suspend fun getProgressionForExercise(exerciseId: String): List<SetLog>

    @Query("""
        SELECT * FROM set_log
         WHERE exerciseId = :exerciseId
         ORDER BY completedAt DESC
         LIMIT :limit
    """)
    suspend fun getRecent(exerciseId: String, limit: Int = 1): List<SetLog>

    @Query("DELETE FROM set_log WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM set_log")
    suspend fun getAll(): List<SetLog>
}
