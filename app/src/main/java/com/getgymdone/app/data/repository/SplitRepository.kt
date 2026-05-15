package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.DayExerciseDao
import com.getgymdone.app.data.db.dao.SplitDao
import com.getgymdone.app.data.db.dao.WorkoutDayDao
import com.getgymdone.app.data.db.entities.DayExercise
import com.getgymdone.app.data.db.entities.Split
import com.getgymdone.app.data.db.entities.WorkoutDay
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
    suspend fun getDay(splitId: String, dayNumber: Int): WorkoutDay? = dayDao.getByDayNumber(splitId, dayNumber)
    suspend fun getDayExercises(workoutDayId: String): List<DayExercise> = dayExerciseDao.getByDay(workoutDayId)
}
