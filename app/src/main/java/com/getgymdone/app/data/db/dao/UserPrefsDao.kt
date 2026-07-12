package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.UserPrefs
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPrefsDao {
    @Query("SELECT * FROM user_prefs WHERE id = 0 LIMIT 1")
    fun observe(): Flow<UserPrefs?>

    @Query("SELECT * FROM user_prefs WHERE id = 0 LIMIT 1")
    suspend fun get(): UserPrefs?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prefs: UserPrefs)
}
