package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friend ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Friend>>

    @Query("SELECT * FROM friend")
    suspend fun getAll(): List<Friend>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(friend: Friend)

    @Query("DELETE FROM friend WHERE userId = :userId")
    suspend fun delete(userId: String)
}
