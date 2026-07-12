package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.SessionDao
import com.getgymdone.app.data.db.dao.SetLogDao
import com.getgymdone.app.data.db.entities.Session
import com.getgymdone.app.data.db.entities.SetLog
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Marks a session as a logged rest day rather than a workout, so counts can exclude it. */
const val REST_SESSION_NOTE = "rest"

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
) {
    /** Record a rest day as completed for today — shows on the calendar, but logs no sets. */
    suspend fun completeRestDay(workoutDayId: String) {
        val now = System.currentTimeMillis()
        sessionDao.insert(
            Session(
                id = UUID.randomUUID().toString(),
                workoutDayId = workoutDayId,
                startedAt = now,
                completedAt = now,
                notes = REST_SESSION_NOTE,
            ),
        )
    }

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

    /**
     * Record a non-gym activity (running, a sport, etc.) as a completed session. It always counts
     * toward the streak / calendar / consistency. When [workoutDayId] is null it's a standalone
     * activity that never advances the rotation; passing today's day id instead counts it *as*
     * that day's workout, so the rotation moves on and the day earns its checkmark.
     */
    suspend fun logActivity(activityType: String, durationMin: Int?, notes: String?, workoutDayId: String? = null) {
        val now = System.currentTimeMillis()
        sessionDao.insert(
            Session(
                id = UUID.randomUUID().toString(),
                workoutDayId = workoutDayId,
                startedAt = now,
                completedAt = now,
                notes = notes?.takeIf { it.isNotBlank() },
                activityType = activityType,
                durationMin = durationMin,
            ),
        )
    }

    suspend fun completeSession(id: String) =
        sessionDao.complete(id, System.currentTimeMillis())

    suspend fun getById(id: String): Session? = sessionDao.getById(id)
    suspend fun getInProgress(): Session? = sessionDao.getInProgress()
    suspend fun getInProgressForDay(workoutDayId: String): Session? = sessionDao.getInProgressForDay(workoutDayId)
    suspend fun getLastCompleted(): Session? = sessionDao.getLastCompleted()
    suspend fun getLastCompletedWorkout(): Session? = sessionDao.getLastCompletedWorkout()
    fun observeHistory(): Flow<List<Session>> = sessionDao.observeHistory()

    /** Logs a completed set and returns the new row id so callers can undo it. */
    suspend fun logSet(
        sessionId: String,
        exerciseId: String,
        setNumber: Int,
        weightKg: Double,
        reps: Int,
    ): String {
        val id = UUID.randomUUID().toString()
        setLogDao.insert(
            SetLog(
                id = id,
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                weightKg = weightKg,
                reps = reps,
                completedAt = System.currentTimeMillis(),
            )
        )
        return id
    }

    suspend fun deleteSet(id: String) = setLogDao.deleteById(id)

    suspend fun getSetsForSession(sessionId: String): List<SetLog> =
        setLogDao.getBySession(sessionId)

    suspend fun getProgression(exerciseId: String): List<SetLog> =
        setLogDao.getProgressionForExercise(exerciseId)

    /** Progression history limited to completed sessions — used for progressive-overload advice. */
    suspend fun getCompletedProgression(exerciseId: String): List<SetLog> =
        setLogDao.getCompletedProgression(exerciseId)

    suspend fun getLastSet(exerciseId: String): SetLog? =
        setLogDao.getRecent(exerciseId, limit = 1).firstOrNull()

    /** Sets from the last completed session of this exercise, for per-set weight/rep memory. */
    suspend fun getLastSessionSets(exerciseId: String): List<SetLog> =
        setLogDao.getLastCompletedSessionSets(exerciseId)
}
