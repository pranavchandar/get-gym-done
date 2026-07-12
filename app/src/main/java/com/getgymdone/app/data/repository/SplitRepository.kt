package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.DayExerciseDao
import com.getgymdone.app.data.db.dao.SessionDao
import com.getgymdone.app.data.db.dao.SplitDao
import com.getgymdone.app.data.db.dao.WorkoutDayDao
import com.getgymdone.app.data.db.entities.DayExercise
import com.getgymdone.app.data.db.entities.Split
import com.getgymdone.app.data.db.entities.WorkoutDay
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SplitRepository @Inject constructor(
    private val splitDao: SplitDao,
    private val dayDao: WorkoutDayDao,
    private val dayExerciseDao: DayExerciseDao,
    private val sessionDao: SessionDao,
) {
    fun observeAll(): Flow<List<Split>> = splitDao.observeAll()
    /** Emits whenever any split's day layout changes — drives live refresh of Home/Workouts. */
    fun observeDays(): Flow<List<WorkoutDay>> = dayDao.observeAll()
    /** Emits whenever any day's exercise list changes. */
    fun observeDayExercises(): Flow<List<DayExercise>> = dayExerciseDao.observeAll()
    suspend fun getById(id: String): Split? = splitDao.getById(id)
    suspend fun getDays(splitId: String): List<WorkoutDay> = dayDao.getBySplit(splitId)
    /** Every workout day across all splits — used to resolve sessions logged under any routine. */
    suspend fun getAllDays(): List<WorkoutDay> = dayDao.getAll()
    suspend fun getDayById(workoutDayId: String): WorkoutDay? = dayDao.getById(workoutDayId)
    suspend fun getDay(splitId: String, dayNumber: Int): WorkoutDay? = dayDao.getByDayNumber(splitId, dayNumber)
    suspend fun getDayExercises(workoutDayId: String): List<DayExercise> = dayExerciseDao.getByDay(workoutDayId)

    /**
     * Replace a day's exercise list with [exercises], in the given order. Ids are re-derived from
     * the day + exercise + position so the rows stay stable and idempotent across edits.
     */
    suspend fun setDayExercises(workoutDayId: String, exercises: List<DayExerciseEdit>) {
        val rows = exercises.mapIndexed { idx, e ->
            DayExercise(
                id = "${workoutDayId}_${e.exerciseId}_$idx",
                workoutDayId = workoutDayId,
                exerciseId = e.exerciseId,
                orderIndex = idx,
                prescribedSets = e.sets,
                prescribedRepsLow = e.repsLow,
                prescribedRepsHigh = e.repsHigh,
            )
        }
        dayExerciseDao.replaceForDay(workoutDayId, rows)
    }

    /** How many logged sessions reference a day — used to guard against removing trained days. */
    suspend fun sessionCountForDay(workoutDayId: String): Int = sessionDao.countForDay(workoutDayId)

    /** Append a new empty training day to a split. The split becomes user-owned. */
    suspend fun addDay(splitId: String) {
        val days = dayDao.getBySplit(splitId)
        val nextNumber = (days.maxOfOrNull { it.dayNumber } ?: 0) + 1
        dayDao.upsertAll(
            listOf(
                WorkoutDay(
                    id = "${splitId}_d${UUID.randomUUID().toString().take(6)}",
                    splitId = splitId,
                    dayNumber = nextNumber,
                    name = "New Day",
                    muscleGroups = emptyList(),
                    isRestDay = false,
                ),
            ),
        )
        splitDao.setDayCount(splitId, days.size + 1)
        splitDao.markCustom(splitId)
    }

    /**
     * Remove a day from its split, then renumber the rest to stay contiguous. Refuses if the day
     * has logged sessions (which the FK would block anyway) — returns false so the caller can warn.
     */
    suspend fun removeDay(workoutDayId: String): Boolean {
        if (sessionDao.countForDay(workoutDayId) > 0) return false
        val day = dayDao.getById(workoutDayId) ?: return false
        dayExerciseDao.deleteByDay(workoutDayId)
        dayDao.deleteById(workoutDayId)
        val remaining = dayDao.getBySplit(day.splitId).sortedBy { it.dayNumber }
        remaining.forEachIndexed { idx, d -> if (d.dayNumber != idx + 1) dayDao.setDayNumber(d.id, idx + 1) }
        splitDao.setDayCount(day.splitId, remaining.size)
        splitDao.markCustom(day.splitId)
        return true
    }

    /** Rename a day in place (no REPLACE, so its exercises survive). */
    suspend fun renameDay(workoutDayId: String, name: String) {
        dayDao.setName(workoutDayId, name.ifBlank { "Day" })
    }

    /** Swap two days' positions (used by move up / move down). The split becomes user-owned. */
    suspend fun swapDays(firstDayId: String, secondDayId: String) {
        val a = dayDao.getById(firstDayId) ?: return
        val b = dayDao.getById(secondDayId) ?: return
        dayDao.setDayNumber(a.id, b.dayNumber)
        dayDao.setDayNumber(b.id, a.dayNumber)
        splitDao.markCustom(a.splitId)
    }

    /**
     * Persist a user-defined split with its days and exercise prescriptions. Returns the new
     * split's id so the caller can mark it as the active split.
     */
    suspend fun createCustom(name: String, days: List<CustomDayDraft>): String {
        val splitId = "custom_${UUID.randomUUID().toString().take(8)}"
        splitDao.upsert(
            Split(id = splitId, name = name, dayCount = days.size, isCustom = true),
        )
        val workoutDays = days.mapIndexed { idx, d ->
            WorkoutDay(
                id = "${splitId}_d${idx + 1}",
                splitId = splitId,
                dayNumber = idx + 1,
                name = d.name,
                muscleGroups = d.muscleGroups,
            )
        }
        dayDao.upsertAll(workoutDays)
        val items = days.flatMapIndexed { idx, d ->
            val dayId = workoutDays[idx].id
            d.exercises.mapIndexed { exIdx, ex ->
                DayExercise(
                    id = "${dayId}_${ex.exerciseId}_$exIdx",
                    workoutDayId = dayId,
                    exerciseId = ex.exerciseId,
                    orderIndex = exIdx,
                    prescribedSets = ex.sets,
                    prescribedRepsLow = ex.repsLow,
                    prescribedRepsHigh = ex.repsHigh,
                )
            }
        }
        dayExerciseDao.upsertAll(items)
        return splitId
    }
}

data class CustomDayDraft(
    val name: String,
    val muscleGroups: List<String>,
    val exercises: List<CustomExerciseDraft>,
)

data class CustomExerciseDraft(
    val exerciseId: String,
    val sets: Int,
    val repsLow: Int,
    val repsHigh: Int,
)

/** A single exercise prescription used when editing an existing day's exercise list. */
data class DayExerciseEdit(
    val exerciseId: String,
    val sets: Int,
    val repsLow: Int,
    val repsHigh: Int,
)
