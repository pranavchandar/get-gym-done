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

    /**
     * All sets from the most recent *completed* session that included this exercise, ordered by
     * set number. Powers per-set weight/rep memory: each set prefills with what was last done for
     * that set position. Excludes the in-progress session so resuming a workout still shows last
     * time's numbers as defaults.
     */
    @Query("""
        SELECT sl.* FROM set_log sl
         WHERE sl.exerciseId = :exerciseId
           AND sl.sessionId = (
               SELECT s.id FROM session s
                JOIN set_log x ON x.sessionId = s.id
                WHERE x.exerciseId = :exerciseId AND s.completedAt IS NOT NULL
                ORDER BY s.completedAt DESC
                LIMIT 1
           )
         ORDER BY sl.setNumber
    """)
    suspend fun getLastCompletedSessionSets(exerciseId: String): List<SetLog>

    @Query("DELETE FROM set_log WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM set_log")
    suspend fun getAll(): List<SetLog>
}
