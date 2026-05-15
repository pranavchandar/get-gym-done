package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.SessionDao
import com.getgymdone.app.data.db.dao.SetLogDao
import com.getgymdone.app.data.db.entities.Session
import com.getgymdone.app.data.db.entities.SetLog
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
) {
    suspend fun startSession(workoutDayId: String): Session {
        val s = Session(
            id = UUID.randomUUID().toString(),
            workoutDayId = workoutDayId,
            startedAt = System.currentTimeMillis(),
            completedAt = null,
            notes = null,
        )
        sessionDao.insert(s)
        return s
    }

    suspend fun completeSession(id: String) =
        sessionDao.complete(id, System.currentTimeMillis())

    suspend fun getInProgress(): Session? = sessionDao.getInProgress()
    suspend fun getLastCompleted(): Session? = sessionDao.getLastCompleted()
    fun observeHistory(): Flow<List<Session>> = sessionDao.observeHistory()

    suspend fun logSet(
        sessionId: String,
        exerciseId: String,
        setNumber: Int,
        weightKg: Double,
        reps: Int,
    ) {
        setLogDao.insert(
            SetLog(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                weightKg = weightKg,
                reps = reps,
                completedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun getSetsForSession(sessionId: String): List<SetLog> =
        setLogDao.getBySession(sessionId)

    suspend fun getProgression(exerciseId: String): List<SetLog> =
        setLogDao.getProgressionForExercise(exerciseId)

    suspend fun getLastSet(exerciseId: String): SetLog? =
        setLogDao.getRecent(exerciseId, limit = 1).firstOrNull()
}
