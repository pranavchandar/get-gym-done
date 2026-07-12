package com.getgymdone.app.data.repository

import android.content.Context
import android.net.Uri
import com.getgymdone.app.data.db.dao.ExerciseMediaDao
import com.getgymdone.app.data.db.entities.ExerciseMedia
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Stores user-uploaded exercise media (images / GIFs) entirely on-device. Picked content URIs are
 * copied into app-internal storage immediately, so nothing leaves the phone and the files survive
 * after the transient picker permission is gone.
 */
@Singleton
class ExerciseMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ExerciseMediaDao,
) {
    private val mediaDir: File by lazy {
        File(context.filesDir, "exercise_media").apply { mkdirs() }
    }

    fun observe(exerciseId: String): Flow<List<ExerciseMedia>> = dao.observeForExercise(exerciseId)

    /** Copy the picked [uri] into local storage and record it against [exerciseId]. */
    suspend fun add(exerciseId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val ext = when (context.contentResolver.getType(uri)) {
            "image/gif" -> "gif"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val file = File(mediaDir, "${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext
        dao.insert(
            ExerciseMedia(
                id = UUID.randomUUID().toString(),
                exerciseId = exerciseId,
                filePath = file.absolutePath,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Remove a media entry and delete its backing file. */
    suspend fun remove(mediaId: String) = withContext(Dispatchers.IO) {
        val media = dao.getById(mediaId) ?: return@withContext
        runCatching { File(media.filePath).delete() }
        dao.deleteById(mediaId)
    }
}
