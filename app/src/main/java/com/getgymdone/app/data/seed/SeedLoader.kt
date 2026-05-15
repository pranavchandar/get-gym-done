package com.getgymdone.app.data.seed

import android.content.Context
import com.getgymdone.app.R
import com.getgymdone.app.data.db.AppDatabase
import com.getgymdone.app.data.db.entities.DayExercise
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.Split
import com.getgymdone.app.data.db.entities.WorkoutDay
import kotlinx.serialization.json.Json

class SeedLoader(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun seedIfEmpty() {
        if (db.exerciseDao().getAll().isNotEmpty()) return
        val text = context.resources.openRawResource(R.raw.seed_data).bufferedReader().use { it.readText() }
        val seed = json.decodeFromString(SeedFile.serializer(), text)

        db.exerciseDao().upsertAll(seed.exercises.map { it.toEntity() })

        seed.splits.forEach { s ->
            db.splitDao().upsert(Split(id = s.id, name = s.name, dayCount = s.dayCount, isCustom = false))
            val days = s.days.map { d ->
                WorkoutDay(
                    id = d.id,
                    splitId = s.id,
                    dayNumber = d.dayNumber,
                    name = d.name,
                    muscleGroups = d.muscleGroups,
                )
            }
            db.workoutDayDao().upsertAll(days)

            val items = s.days.flatMap { d ->
                d.exercises.map { de ->
                    DayExercise(
                        id = "${d.id}_${de.exerciseId}_${de.orderIndex}",
                        workoutDayId = d.id,
                        exerciseId = de.exerciseId,
                        orderIndex = de.orderIndex,
                        prescribedSets = de.sets,
                        prescribedRepsLow = de.repsLow,
                        prescribedRepsHigh = de.repsHigh,
                    )
                }
            }
            db.dayExerciseDao().upsertAll(items)
        }
    }
}

private fun SeedExercise.toEntity(): Exercise = Exercise(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles,
    illustrationFilename = illustrationFilename,
    formCues = formCues,
    equipment = equipment,
    defaultSets = defaultSets,
    defaultRepsLow = defaultRepsLow,
    defaultRepsHigh = defaultRepsHigh,
)
