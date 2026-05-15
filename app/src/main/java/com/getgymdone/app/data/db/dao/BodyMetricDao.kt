package com.getgymdone.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getgymdone.app.data.db.entities.BodyMetric
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metric: BodyMetric)

    @Query("SELECT * FROM body_metric ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLatest(): BodyMetric?

    @Query("SELECT * FROM body_metric ORDER BY recordedAt ASC")
    fun observeHistory(): Flow<List<BodyMetric>>

    @Query("SELECT * FROM body_metric")
    suspend fun getAll(): List<BodyMetric>
}
