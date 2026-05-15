package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.ExerciseDao
import com.getgymdone.app.data.db.entities.Exercise
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    suspend fun getById(id: String): Exercise? = dao.getById(id)
}
