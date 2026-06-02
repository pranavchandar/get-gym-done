package com.getgymdone.app.data.seed

import android.content.Context
import com.getgymdone.app.R
import com.getgymdone.app.data.db.AppDatabase
import com.getgymdone.app.data.db.entities.DayExercise
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.Split
import com.getgymdone.app.data.db.entities.UserPrefs
import com.getgymdone.app.data.db.entities.WorkoutDay
import kotlinx.serialization.json.Json

class SeedLoader(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun seedIfEmpty() {
        // Seed-version migration: if the user has any splits but is missing a required
        // marker id, wipe and reload the catalog. Using "missing marker" rather than
        // "legacy id present" lets us add a split back to the catalog without causing a
        // perpetual migration loop (a legacy-id trigger would re-fire after the reload
        // re-inserted the legacy id). FK CASCADE on workout_day/day_exercise wipes too.
        val ids = db.splitDao().getAll().map { it.id }.toSet()
        if (ids.isNotEmpty() && SEED_MARKERS.any { it !in ids }) {
            db.splitDao().clear()
            db.exerciseDao().clear()
            db.userPrefsDao().upsert(UserPrefs())
        }
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

    companion object {
        // Split ids that MUST be present after seeding. Bump this set whenever the
        // catalog grows: existing installs auto-reseed on next launch because they're
        // missing the new marker, then settle (no further triggers fire).
        private val SEED_MARKERS = setOf(
            "5day_illustrated",
            "arnold_6day",
            "phul_4day",
            "glute_focused_5day",
        )
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
