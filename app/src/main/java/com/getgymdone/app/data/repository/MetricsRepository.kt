package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.BodyMetricDao
import com.getgymdone.app.data.db.dao.ExerciseDao
import com.getgymdone.app.data.db.dao.SessionDao
import com.getgymdone.app.data.db.dao.SetLogDao
import com.getgymdone.app.data.db.entities.BodyMetric
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.Session
import com.getgymdone.app.data.db.entities.SetLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only analytics over the logged data. Powers Profile / Metrics, the Workout-Complete
 * summary, and the Home stat row. Everything is computed off the raw [SetLog] / [Session]
 * tables so it stays correct no matter which split is active.
 */
@Singleton
class MetricsRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
    private val bodyMetricDao: BodyMetricDao,
    private val exerciseDao: ExerciseDao,
) {
    suspend fun completedSessions(): List<Session> =
        sessionDao.getAll().filter { it.completedAt != null }.sortedBy { it.completedAt }

    suspend fun allSets(): List<SetLog> = setLogDao.getAll()

    suspend fun setsForSession(sessionId: String): List<SetLog> =
        setLogDao.getBySession(sessionId)

    suspend fun exercises(): List<Exercise> = exerciseDao.getAll()

    suspend fun latestBodyMetric(): BodyMetric? = bodyMetricDao.getLatest()

    suspend fun bodyweightSeries(): List<BodyMetric> =
        bodyMetricDao.getAll().filter { it.bodyweightKg != null }.sortedBy { it.recordedAt }

    /** Total tonnage across every logged set, in kg. */
    suspend fun totalVolumeKg(): Double =
        setLogDao.getAll().sumOf { it.weightKg * it.reps }

    /** Sessions completed within the last [days] days, inclusive of today. */
    suspend fun sessionsInLastDays(days: Int): Int {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        return completedSessions().count { (it.completedAt ?: 0) >= cutoff }
    }

    /**
     * Current streak in consecutive calendar days that ended on a completed session, counting
     * back from the most recent one. A gap of more than a day breaks it.
     */
    suspend fun currentStreakDays(): Int {
        val days = completedSessions()
            .mapNotNull { it.completedAt }
            .map { it / (24L * 60 * 60 * 1000) }
            .distinct()
            .sortedDescending()
        if (days.isEmpty()) return 0
        var streak = 1
        for (i in 1 until days.size) {
            if (days[i - 1] - days[i] == 1L) streak++ else break
        }
        return streak
    }
}