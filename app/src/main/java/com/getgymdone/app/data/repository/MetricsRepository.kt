package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.BodyMetricDao
import com.getgymdone.app.data.db.dao.ExerciseDao
import com.getgymdone.app.data.db.dao.SessionDao
import com.getgymdone.app.data.db.dao.SetLogDao
import com.getgymdone.app.data.db.entities.BodyMetric
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.Session
import com.getgymdone.app.data.db.entities.SetLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
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

    /** Today's body-metric row, if one has already been logged this calendar day. */
    suspend fun todayBodyMetric(): BodyMetric? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return bodyMetricDao.getAll().firstOrNull {
            Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate() == today
        }
    }

    /** All logged body metrics, oldest first. */
    suspend fun bodyMetrics(): List<BodyMetric> =
        bodyMetricDao.getAll().sortedBy { it.recordedAt }

    suspend fun bodyweightSeries(): List<BodyMetric> =
        bodyMetricDao.getAll().filter { it.bodyweightKg != null }.sortedBy { it.recordedAt }

    /**
     * Record a body-metric entry. Any combination of fields may be supplied; a no-op if all are
     * null. One entry per calendar day: re-logging on the same day overwrites that day's row
     * (only the fields provided this time change; others keep their existing value), so the day
     * isn't duplicated on the graphs.
     */
    suspend fun logBodyMetric(bodyweightKg: Double?, bodyFatPct: Double?, muscleMassKg: Double?) {
        if (bodyweightKg == null && bodyFatPct == null && muscleMassKg == null) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val existing = bodyMetricDao.getAll().firstOrNull {
            Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate() == today
        }
        bodyMetricDao.insert(
            BodyMetric(
                id = existing?.id ?: UUID.randomUUID().toString(),
                recordedAt = System.currentTimeMillis(),
                bodyweightKg = bodyweightKg ?: existing?.bodyweightKg,
                bodyFatPct = bodyFatPct ?: existing?.bodyFatPct,
                muscleMassKg = muscleMassKg ?: existing?.muscleMassKg,
            ),
        )
    }

    /** Total tonnage across every logged set, in kg. */
    suspend fun totalVolumeKg(): Double =
        setLogDao.getAll().sumOf { it.weightKg * it.reps }

    /** Sessions completed within the last [days] days, inclusive of today. */
    suspend fun sessionsInLastDays(days: Int): Int {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        return completedSessions().count { (it.completedAt ?: 0) >= cutoff }
    }

    /**
     * Current streak in calendar days, counting back from the most recent completed session.
     * Strictly consecutive days extend it; a gap is bridged — and the skipped days counted toward
     * the streak — only when it spans no more than [maxRestGap] days, the routine's longest run of
     * scheduled rest days. So a planned rest day in the middle never breaks the streak, but missing
     * more days than the schedule allows does.
     *
     * The same allowance applies to the gap between today and the most recent session: once you've
     * been away longer than the schedule's rest run permits, the streak is **lost** and this returns
     * 0 — it no longer reflects a stale run that ended days ago.
     */
    suspend fun currentStreakDays(maxRestGap: Int = 0): Int {
        val zone = ZoneId.systemDefault()
        val days = completedSessions()
            .mapNotNull { it.completedAt }
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
            .distinct()
            .sortedDescending()
        if (days.isEmpty()) return 0
        // Lost if the last session is further back than a planned rest run could bridge.
        if (LocalDate.now(zone).toEpochDay() - days.first() > maxRestGap + 1L) return 0
        var streak = 1L
        for (i in 1 until days.size) {
            val gap = days[i - 1] - days[i]
            if (gap in 1..(maxRestGap + 1L)) streak += gap else break
        }
        return streak.toInt()
    }

    /**
     * Longest streak ever achieved, in calendar days, under the same rest-aware bridging rule as
     * [currentStreakDays]. Unlike the current streak this never "expires" — it's a personal best.
     */
    suspend fun longestStreakDays(maxRestGap: Int = 0): Int {
        val zone = ZoneId.systemDefault()
        val days = completedSessions()
            .mapNotNull { it.completedAt }
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
            .distinct()
            .sorted()
        if (days.isEmpty()) return 0
        var longest = 1L
        var run = 1L
        for (i in 1 until days.size) {
            val gap = days[i] - days[i - 1]
            run = if (gap in 1..(maxRestGap + 1L)) run + gap else 1L
            if (run > longest) longest = run
        }
        return longest.toInt()
    }
}