package com.getgymdone.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

const val NUDGE_CHANNEL_ID = "nudges"
private const val NUDGE_ID_BASE = 5200

/** Posts a local notification when a friend nudges you to keep an at-risk streak alive. */
object NudgeNotifier {

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(NUDGE_CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    NUDGE_CHANNEL_ID,
                    "Friend nudges",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "When a friend pokes you to keep your streak alive"
                    enableVibration(true)
                },
            )
        }
    }

    fun show(context: Context, fromHandle: String, streakDays: Int) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        ensureChannel(context)

        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPi = openIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val text = if (streakDays > 0) {
            "$fromHandle says: don't break your $streakDays-day streak 🔥"
        } else {
            "$fromHandle nudged you to train today 🔥"
        }
        val notification = NotificationCompat.Builder(context, NUDGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Keep your streak alive")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .apply { contentPi?.let(::setContentIntent) }
            .build()

        // Distinct id per sender so several nudges don't overwrite each other.
        NotificationManagerCompat.from(context)
            .notify(NUDGE_ID_BASE + (fromHandle.hashCode() and 0xFFFF), notification)
    }
}
