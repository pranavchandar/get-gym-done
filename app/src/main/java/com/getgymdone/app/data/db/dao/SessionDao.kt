package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session)

    @Query("UPDATE session SET completedAt = :completedAt WHERE id = :id")
    suspend fun complete(id: String, completedAt: Long)

    @Query("SELECT * FROM session WHERE completedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getInProgress(): Session?

    @Query("SELECT * FROM session WHERE completedAt IS NOT NULL ORDER BY completedAt DESC")
    fun observeHistory(): Flow<List<Session>>

    @Query("SELECT * FROM session WHERE completedAt IS NOT NULL ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLastCompleted(): Session?

    @Query("SELECT * FROM session")
    suspend fun getAll(): List<Session>
}
