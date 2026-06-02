package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.Split
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {
    @Query("SELECT * FROM split ORDER BY name")
    fun observeAll(): Flow<List<Split>>

    @Query("SELECT * FROM split")
    suspend fun getAll(): List<Split>

    @Query("SELECT * FROM split WHERE id = :id")
    suspend fun getById(id: String): Split?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Split>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Split)

    @Query("DELETE FROM split")
    suspend fun clear()
}
