package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.DayExerciseDao
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
) {
    fun observeAll(): Flow<List<Split>> = splitDao.observeAll()
    suspend fun getById(id: String): Split? = splitDao.getById(id)
    suspend fun getDays(splitId: String): List<WorkoutDay> = dayDao.getBySplit(splitId)
    suspend fun getDayById(workoutDayId: String): WorkoutDay? = dayDao.getById(workoutDayId)
    suspend fun getDay(splitId: String, dayNumber: Int): WorkoutDay? = dayDao.getByDayNumber(splitId, dayNumber)
    suspend fun getDayExercises(workoutDayId: String): List<DayExercise> = dayExerciseDao.getByDay(workoutDayId)

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
