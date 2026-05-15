package com.getgymdone.app.data.repository

import android.content.Context
import android.net.Uri
import com.getgymdone.app.data.db.AppDatabase
import com.getgymdone.app.data.db.entities.BodyMetric
import com.getgymdone.app.data.db.entities.DayExercise
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.Session
import com.getgymdone.app.data.db.entities.SetLog
import com.getgymdone.app.data.db.entities.Split
import com.getgymdone.app.data.db.entities.UserPrefs
import com.getgymdone.app.data.db.entities.WorkoutDay
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val version: Int,
    val exportedAt: Long,
    val exercises: List<Exercise>,
    val splits: List<Split>,
    val workoutDays: List<WorkoutDay>,
    val dayExercises: List<DayExercise>,
    val sessions: List<Session>,
    val setLogs: List<SetLog>,
    val bodyMetrics: List<BodyMetric>,
    val userPrefs: UserPrefs?,
)

@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = BackupFile(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            exercises = db.exerciseDao().getAll(),
            splits = db.splitDao().getAll(),
            workoutDays = db.workoutDayDao().getAll(),
            dayExercises = db.dayExerciseDao().getAll(),
            sessions = db.sessionDao().getAll(),
            setLogs = db.setLogDao().getAll(),
            bodyMetrics = db.bodyMetricDao().getAll(),
            userPrefs = db.userPrefsDao().get(),
        )
        val text = json.encodeToString(BackupFile.serializer(), backup)
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) }
            ?: error("Could not open output stream for $uri")
    }

    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not open input stream for $uri")
        val backup = json.decodeFromString(BackupFile.serializer(), text)

        db.exerciseDao().upsertAll(backup.exercises)
        db.splitDao().upsertAll(backup.splits)
        db.workoutDayDao().upsertAll(backup.workoutDays)
        db.dayExerciseDao().upsertAll(backup.dayExercises)
        backup.userPrefs?.let { db.userPrefsDao().upsert(it) }
    }
}
