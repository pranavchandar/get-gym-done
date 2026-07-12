package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "body_metric")
data class BodyMetric(
    @PrimaryKey val id: String,
    val recordedAt: Long,
    val bodyweightKg: Double?,
    val bodyFatPct: Double?,
    val muscleMassKg: Double?,
)
