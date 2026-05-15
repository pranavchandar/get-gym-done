package com.getgymdone.app.data.repository

import com.getgymdone.app.data.db.dao.UserPrefsDao
import com.getgymdone.app.data.db.entities.UserPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class UserPrefsRepository @Inject constructor(
    private val dao: UserPrefsDao,
) {
    fun observe(): Flow<UserPrefs> = dao.observe().map { it ?: UserPrefs() }

    suspend fun get(): UserPrefs = dao.get() ?: UserPrefs().also { dao.upsert(it) }

    suspend fun update(transform: (UserPrefs) -> UserPrefs) {
        val current = get()
        dao.upsert(transform(current))
    }
}
