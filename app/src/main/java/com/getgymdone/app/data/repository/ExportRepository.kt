package com.getgymdone.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.getgymdone.app.data.db.AppDatabase
import com.getgymdone.app.data.db.entities.BodyMetric
import com.getgymdone.app.data.db.entities.DayExercise
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.ExerciseMedia
import com.getgymdone.app.data.db.entities.Session
import com.getgymdone.app.data.db.entities.SetLog
import com.getgymdone.app.data.db.entities.Split
import com.getgymdone.app.data.db.entities.UserPrefs
import com.getgymdone.app.data.db.entities.WorkoutDay
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A user-uploaded media file, carried in the backup with its bytes base64-encoded so it can be
 * restored on any device (the on-device file path isn't portable, so it's regenerated on import).
 */
@Serializable
data class BackupMedia(
    val id: String,
    val exerciseId: String,
    val addedAt: Long,
    val ext: String,
    val dataBase64: String,
)

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
    val exerciseMedia: List<BackupMedia> = emptyList(),
)

@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val mediaDir: File by lazy { File(context.filesDir, "exercise_media").apply { mkdirs() } }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        // Embed each media file's bytes so the backup is self-contained and portable. Files that
        // have gone missing are skipped rather than failing the whole export.
        val media = db.exerciseMediaDao().getAll().mapNotNull { m ->
            val f = File(m.filePath)
            if (!f.exists()) return@mapNotNull null
            BackupMedia(
                id = m.id,
                exerciseId = m.exerciseId,
                addedAt = m.addedAt,
                ext = f.extension.ifEmpty { "img" },
                dataBase64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP),
            )
        }
        val backup = BackupFile(
            version = 2,
            exportedAt = System.currentTimeMillis(),
            exercises = db.exerciseDao().getAll(),
            splits = db.splitDao().getAll(),
            workoutDays = db.workoutDayDao().getAll(),
            dayExercises = db.dayExerciseDao().getAll(),
            sessions = db.sessionDao().getAll(),
            setLogs = db.setLogDao().getAll(),
            bodyMetrics = db.bodyMetricDao().getAll(),
            userPrefs = db.userPrefsDao().get(),
            exerciseMedia = media,
        )
        val text = json.encodeToString(BackupFile.serializer(), backup)
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) }
            ?: error("Could not open output stream for $uri")
    }

    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not open input stream for $uri")
        val backup = json.decodeFromString(BackupFile.serializer(), text)

        // Full restore: wipe everything, then re-insert parents → children so foreign keys hold.
        db.clearAllTables()
        db.exerciseDao().upsertAll(backup.exercises)
        db.splitDao().upsertAll(backup.splits)
        db.workoutDayDao().upsertAll(backup.workoutDays)
        db.dayExerciseDao().upsertAll(backup.dayExercises)
        backup.sessions.forEach { db.sessionDao().insert(it) }
        backup.setLogs.forEach { db.setLogDao().insert(it) }
        backup.bodyMetrics.forEach { db.bodyMetricDao().insert(it) }
        db.userPrefsDao().upsert(backup.userPrefs ?: UserPrefs())

        // Restore media: write the bytes back into app storage and point the row at the new file.
        // The extension comes from an untrusted backup, so sanitize it and confirm the resulting
        // path stays inside mediaDir before writing (guards against path traversal).
        val mediaRoot = mediaDir.canonicalPath + File.separator
        backup.exerciseMedia.forEach { m ->
            val ext = m.ext.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "img"
            val file = File(mediaDir, "${UUID.randomUUID()}.$ext")
            runCatching {
                if (!file.canonicalPath.startsWith(mediaRoot)) return@runCatching
                file.writeBytes(Base64.decode(m.dataBase64, Base64.NO_WRAP))
                db.exerciseMediaDao().insert(
                    ExerciseMedia(
                        id = m.id,
                        exerciseId = m.exerciseId,
                        filePath = file.absolutePath,
                        addedAt = m.addedAt,
                    ),
                )
            }
        }
    }
}
