package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.ExerciseDao
import com.getgymdone.app.data.db.entities.Exercise
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    suspend fun all(): List<Exercise> = dao.getAll()
    suspend fun getById(id: String): Exercise? = dao.getById(id)

    /** Persist a user-created exercise and return it. Categorized by [primaryMuscle]. */
    suspend fun addCustom(
        name: String,
        primaryMuscle: String,
        secondaryMuscles: List<String>,
        equipment: String,
        formCues: List<String>,
        sets: Int,
        repsLow: Int,
        repsHigh: Int,
    ): Exercise {
        val ex = Exercise(
            id = "custom_ex_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            primaryMuscle = primaryMuscle,
            secondaryMuscles = secondaryMuscles,
            illustrationFilename = "",
            formCues = formCues,
            equipment = equipment,
            defaultSets = sets,
            defaultRepsLow = repsLow,
            defaultRepsHigh = repsHigh,
        )
        dao.upsertAll(listOf(ex))
        return ex
    }
}
