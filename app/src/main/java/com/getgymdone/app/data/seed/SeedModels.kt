package com.getgymdone.app.data.seed

import kotlinx.serialization.Serializable

@Serializable
data class SeedFile(
    val splits: List<SeedSplit>,
    val exercises: List<SeedExercise>,
)

@Serializable
data class SeedSplit(
    val id: String,
    val name: String,
    val dayCount: Int,
    val days: List<SeedDay>,
)

@Serializable
data class SeedDay(
    val id: String,
    val dayNumber: Int,
    val name: String,
    val muscleGroups: List<String>,
    val exercises: List<SeedDayExercise>,
    val isRestDay: Boolean = false,
)

@Serializable
data class SeedDayExercise(
    val exerciseId: String,
    val orderIndex: Int,
    val sets: Int,
    val repsLow: Int,
    val repsHigh: Int,
)

@Serializable
data class SeedExercise(
    val id: String,
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val illustrationFilename: String,
    val formCues: List<String>,
    val equipment: String,
    val defaultSets: Int,
    val defaultRepsLow: Int,
    val defaultRepsHigh: Int,
)
