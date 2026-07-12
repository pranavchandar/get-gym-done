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
        if (db.exerciseDao().getAll().isNotEmpty()) {
            mergeNewExercises()
            reconcileCatalogDays()
            return
        }
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
                    isRestDay = d.isRestDay,
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

    /**
     * Insert any catalog exercises added to the seed since this install was first seeded. Only
     * brand-new ids are inserted (existing rows are left untouched — they may be referenced by
     * day_exercise via a RESTRICT foreign key, so a REPLACE-upsert could fail). This lets the
     * exercise picker pick up additions without wiping user data.
     */
    private suspend fun mergeNewExercises() {
        val existing = db.exerciseDao().getAll().map { it.id }.toSet()
        val text = context.resources.openRawResource(R.raw.seed_data).bufferedReader().use { it.readText() }
        val seed = json.decodeFromString(SeedFile.serializer(), text)
        val newOnes = seed.exercises.filter { it.id !in existing }.map { it.toEntity() }
        if (newOnes.isNotEmpty()) db.exerciseDao().upsertAll(newOnes)
        // Backfill descriptive fields (form cues, muscles, defaults) onto catalog exercises that
        // were seeded before those details existed. In-place update keeps day_exercise FKs intact.
        seed.exercises.filter { it.id in existing }.forEach { se ->
            db.exerciseDao().updateDetails(
                id = se.id,
                name = se.name,
                primaryMuscle = se.primaryMuscle,
                secondaryMuscles = se.secondaryMuscles,
                formCues = se.formCues,
                equipment = se.equipment,
                defaultSets = se.defaultSets,
                defaultRepsLow = se.defaultRepsLow,
                defaultRepsHigh = se.defaultRepsHigh,
            )
        }
    }

    /**
     * Converge an already-seeded catalog onto the current seed's day layout (interspersed rest
     * days, renumbered training days). Idempotent and safe for installs with workout history: we
     * only ever INSERT new rest rows and UPDATE dayNumber/dayCount in place — never REPLACE an
     * existing WorkoutDay/Split row, which would CASCADE-wipe day_exercise rows and break the
     * session foreign key. A no-op once the DB already matches the seed.
     */
    private suspend fun reconcileCatalogDays() {
        val existing = db.workoutDayDao().getAll()
        if (existing.isEmpty()) return
        val byId = existing.associateBy { it.id }

        val text = context.resources.openRawResource(R.raw.seed_data).bufferedReader().use { it.readText() }
        val seed = json.decodeFromString(SeedFile.serializer(), text)

        seed.splits.forEach { s ->
            val split = db.splitDao().getById(s.id) ?: return@forEach
            // Once the user has edited a preset's day layout we mark it custom; don't fight them.
            if (split.isCustom) return@forEach
            val newRows = mutableListOf<WorkoutDay>()
            s.days.forEach { d ->
                val current = byId[d.id]
                when {
                    current == null -> newRows += WorkoutDay(
                        id = d.id,
                        splitId = s.id,
                        dayNumber = d.dayNumber,
                        name = d.name,
                        muscleGroups = d.muscleGroups,
                        isRestDay = d.isRestDay,
                    )
                    current.dayNumber != d.dayNumber -> db.workoutDayDao().setDayNumber(d.id, d.dayNumber)
                }
            }
            if (newRows.isNotEmpty()) db.workoutDayDao().upsertAll(newRows)
            if (split.dayCount != s.dayCount) db.splitDao().setDayCount(s.id, s.dayCount)
        }
    }

    companion object {
        // Split ids that MUST be present after seeding. Bump this set whenever the
        // catalog grows: existing installs auto-reseed on next launch because they're
        // missing the new marker, then settle (no further triggers fire).
        private val SEED_MARKERS = setOf(
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
