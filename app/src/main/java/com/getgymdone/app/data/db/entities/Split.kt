package com.getgymdone.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "split")
data class Split(
    @PrimaryKey val id: String,
    val name: String,
    val dayCount: Int,
    val isCustom: Boolean = false,
)
