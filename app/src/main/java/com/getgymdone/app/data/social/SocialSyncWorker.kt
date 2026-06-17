package com.getgymdone.app.data.social

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background sync for the Friends layer: pushes this user's latest stats and pulls
 * friends' + any incoming nudges (posting their notifications). This is what lets a streak-at-risk
 * nudge reach someone who isn't currently in the app. Scheduled only while social is enabled
 * (see [SocialRepository.enableSocial]); [SocialRepository.syncAll] is a no-op if opted out.
 */
@HiltWorker
class SocialSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val social: SocialRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runCatching { social.syncAll() }
        return Result.success()
    }
}
