package com.getgymdone.app.data.social

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.getgymdone.app.data.db.dao.FriendDao
import com.getgymdone.app.data.db.dao.UserPrefsDao
import com.getgymdone.app.data.db.dao.WorkoutDayDao
import com.getgymdone.app.data.db.entities.Friend
import com.getgymdone.app.data.db.entities.Session
import com.getgymdone.app.data.db.entities.UserPrefs
import com.getgymdone.app.data.repository.MetricsRepository
import com.getgymdone.app.data.repository.REST_SESSION_NOTE
import com.getgymdone.app.domain.maxConsecutiveRestDays
import com.getgymdone.app.notifications.NudgeNotifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Owns the opt-in Friends layer. Local-first: the friend graph lives in Room ([FriendDao]) and the
 * backend (Firestore) only ever stores **this user's own** derived [SocialStats] doc — never set
 * logs, weights, or body metrics. Identity is an anonymous Firebase uid; no email or password.
 *
 * Nothing here runs until the user opts in, so a fully-local install never reaches the network.
 */
@Singleton
class SocialRepository @Inject constructor(
    private val userPrefsDao: UserPrefsDao,
    private val friendDao: FriendDao,
    private val workoutDayDao: WorkoutDayDao,
    private val metrics: MetricsRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context,
) {
    private val users get() = firestore.collection("users")

    fun observeFriends(): Flow<List<Friend>> = friendDao.observeAll()
    fun observePrefs(): Flow<UserPrefs?> = userPrefsDao.observe()

    /** Turns the layer on: claims an anonymous identity, saves it, and publishes the first stats. */
    suspend fun enableSocial(handle: String, color: String, photo: String?) {
        val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
        val prefs = userPrefsDao.get() ?: UserPrefs()
        userPrefsDao.upsert(
            prefs.copy(
                socialEnabled = true, socialUserId = uid,
                socialHandle = handle, socialColor = color, avatarPhoto = photo,
            ),
        )
        pushMyStats()
        scheduleSync()
    }

    /** Turns the layer off. Keeps the identity so re-enabling reuses the same uid (and friends). */
    suspend fun disableSocial() {
        val prefs = userPrefsDao.get() ?: return
        userPrefsDao.upsert(prefs.copy(socialEnabled = false))
        cancelSync()
    }

    /** Idempotently schedules the periodic background sync (every ~6h, on a network connection). */
    private fun scheduleSync() {
        val request = PeriodicWorkRequestBuilder<SocialSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancelSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }

    /** This user's shareable QR payload, or null if they haven't opted in yet. */
    suspend fun myCode(): FriendCode? {
        val prefs = userPrefsDao.get() ?: return null
        val uid = prefs.socialUserId ?: return null
        return FriendCode(uid, prefs.socialHandle.orEmpty(), prefs.socialColor ?: "lime")
    }

    /** This user's own derived stats for the leaderboard, or null if opted out. */
    suspend fun myStats(): SocialStats? {
        val prefs = userPrefsDao.get() ?: return null
        if (!prefs.socialEnabled || prefs.socialUserId == null) return null
        return buildMyStats(prefs)
    }

    /**
     * Adds a scanned friend (no-op if it's your own code) and pulls their stats. Also writes a
     * friend-edge under *their* account so you land on their leaderboard too once they next sync —
     * one scan makes the friendship mutual.
     */
    suspend fun addFriend(code: FriendCode) {
        val myUid = userPrefsDao.get()?.socialUserId
        if (code.userId == myUid) return
        val existing = friendDao.getAll().firstOrNull { it.userId == code.userId }
        friendDao.upsert(
            (existing ?: Friend(code.userId, code.handle, code.color, System.currentTimeMillis()))
                .copy(handle = code.handle, color = code.color),
        )
        if (myUid != null) {
            runCatching {
                users.document(code.userId).collection("friends").document(myUid)
                    .set(mapOf("addedAt" to System.currentTimeMillis())).await()
            }
        }
        runCatching { pullFriend(code.userId) }
    }

    /** Removes a friend locally and severs both friend-edges so neither re-syncs the other in. */
    suspend fun removeFriend(userId: String) {
        friendDao.delete(userId)
        val myUid = userPrefsDao.get()?.socialUserId ?: return
        runCatching { users.document(myUid).collection("friends").document(userId).delete().await() }
        runCatching { users.document(userId).collection("friends").document(myUid).delete().await() }
    }

    /** Publishes this user's derived stats to their own backend doc. Skips if opted out. */
    suspend fun pushMyStats() {
        val prefs = userPrefsDao.get() ?: return
        if (!prefs.socialEnabled) return
        val uid = prefs.socialUserId ?: return
        users.document(uid).set(buildMyStats(prefs)).await()
    }

    /** Pulls in anyone who added me, then refreshes every friend's cached stats. */
    suspend fun syncFriends() {
        runCatching { pullIncomingFriends() }
        friendDao.getAll().forEach { runCatching { pullFriend(it.userId) } }
    }

    /** Adds anyone who has added me (an edge under my account) to my local friend list. */
    private suspend fun pullIncomingFriends() {
        val myUid = userPrefsDao.get()?.socialUserId ?: return
        val edges = users.document(myUid).collection("friends").get().await()
        val local = friendDao.getAll().map { it.userId }.toSet()
        edges.documents.forEach { doc ->
            val friendUid = doc.id
            if (friendUid == myUid || friendUid in local) return@forEach
            friendDao.upsert(Friend(userId = friendUid, handle = "", color = "lime", addedAt = System.currentTimeMillis()))
            runCatching { pullFriend(friendUid) }
        }
    }

    /** Push our own stats, pull all friends', and surface any incoming nudges. The sync unit. */
    suspend fun syncAll() {
        pushMyStats()
        syncFriends()
        runCatching { pullNudges() }
    }

    /** Sends a one-shot nudge to a friend whose streak is at risk today. */
    suspend fun nudge(friend: Friend) {
        val prefs = userPrefsDao.get() ?: return
        val myUid = prefs.socialUserId ?: return
        val nudge = Nudge(
            fromUserId = myUid,
            fromHandle = prefs.socialHandle.orEmpty().ifBlank { "A friend" },
            streakDays = friend.currentStreak,
            createdAt = System.currentTimeMillis(),
        )
        users.document(friend.userId).collection("nudges").add(nudge).await()
    }

    /** Surfaces incoming nudges as local notifications, then clears them from the backend. */
    private suspend fun pullNudges() {
        val prefs = userPrefsDao.get() ?: return
        if (!prefs.socialEnabled) return
        val myUid = prefs.socialUserId ?: return
        val snap = users.document(myUid).collection("nudges").get().await()
        snap.documents.forEach { doc ->
            val nudge = doc.toObject(Nudge::class.java) ?: return@forEach
            NudgeNotifier.show(context, nudge.fromHandle, nudge.streakDays)
            runCatching { doc.reference.delete().await() }
        }
    }

    private suspend fun pullFriend(userId: String) {
        val stats = users.document(userId).get().await().toObject(SocialStats::class.java) ?: return
        val existing = friendDao.getAll().firstOrNull { it.userId == userId } ?: return
        friendDao.upsert(
            existing.copy(
                handle = stats.handle.ifBlank { existing.handle },
                color = stats.color.ifBlank { existing.color },
                currentStreak = stats.currentStreak,
                longestStreak = stats.longestStreak,
                streakAtRisk = stats.streakAtRisk,
                weekSessions = stats.weekSessions,
                weekTarget = stats.weekTarget,
                totalSessions = stats.totalSessions,
                lastActiveAt = stats.lastActiveAt.takeIf { it > 0 },
                statsUpdatedAt = stats.updatedAt.takeIf { it > 0 },
                photo = stats.photo.ifBlank { existing.photo },
            ),
        )
    }

    /**
     * The privacy boundary: derives the shareable summary from local data, mirroring Home's
     * "show-up" semantics (trailing-7-day training count, rest-aware streaks, non-rest week target).
     */
    private suspend fun buildMyStats(prefs: UserPrefs): SocialStats {
        val days = prefs.activeSplitId?.let { workoutDayDao.getBySplit(it) }.orEmpty()
        val restGap = maxConsecutiveRestDays(days)
        val completed = metrics.completedSessions()
        val training = completed.filter { it.notes != REST_SESSION_NOTE }
        val weekCutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val currentStreak = metrics.currentStreakDays(restGap)
        return SocialStats(
            handle = prefs.socialHandle.orEmpty(),
            color = prefs.socialColor ?: "lime",
            photo = prefs.avatarPhoto.orEmpty(),
            currentStreak = currentStreak,
            longestStreak = metrics.longestStreakDays(restGap),
            streakAtRisk = isStreakAtRisk(completed, currentStreak, restGap),
            weekSessions = training.count { (it.completedAt ?: 0) >= weekCutoff },
            weekTarget = days.count { !it.isRestDay },
            totalSessions = training.size,
            lastActiveAt = training.maxOfOrNull { it.completedAt ?: 0 } ?: 0,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * The streak is "at risk" when it's alive but today is the make-or-break day: the gap since the
     * last logged day has reached the rest allowance, so missing today would break it (same bridging
     * rule as [MetricsRepository.currentStreakDays]). Computed here, where the schedule is known, and
     * published so friends can nudge accurately rather than guessing.
     */
    private fun isStreakAtRisk(completed: List<Session>, currentStreak: Int, restGap: Int): Boolean {
        if (currentStreak < 1) return false
        val zone = ZoneId.systemDefault()
        val lastDay = completed.mapNotNull { it.completedAt }
            .maxOfOrNull { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
            ?: return false
        return LocalDate.now(zone).toEpochDay() - lastDay == restGap + 1L
    }

    private companion object {
        const val SYNC_WORK_NAME = "social_sync"
    }
}
