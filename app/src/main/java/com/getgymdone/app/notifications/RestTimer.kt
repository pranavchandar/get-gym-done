package com.getgymdone.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.getgymdone.app.R

const val REST_CHANNEL_ID = "rest_timer"
private const val REST_NOTIFICATION_ID = 4201
private const val REST_ALARM_REQUEST = 4202

/**
 * Schedules an exact alarm that fires a notification when a rest period ends — so the user is
 * alerted even when the app is backgrounded and the OS has frozen its coroutines. The on-screen
 * countdown is driven separately off wall-clock time; this only handles the background alert.
 */
object RestTimerScheduler {

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(REST_CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    REST_CHANNEL_ID,
                    "Rest timer",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Alerts you when a rest period is over"
                    enableVibration(true)
                },
            )
        }
    }

    fun schedule(context: Context, endAtMillis: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmPendingIntent(context)
        // Exact + allow-while-idle so it fires on time even in Doze. Fall back to inexact if the
        // user revoked exact-alarm access (won't be perfectly punctual, but still alerts).
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarm.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
        }
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm?.cancel(alarmPendingIntent(context))
        NotificationManagerCompat.from(context).cancel(REST_NOTIFICATION_ID)
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REST_ALARM_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimerScheduler.ensureChannel(context)

        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPi = openIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, REST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Rest's over")
            .setContentText("Time for your next set.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .apply { contentPi?.let(::setContentIntent) }
            .build()

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            NotificationManagerCompat.from(context).notify(REST_NOTIFICATION_ID, notification)
        }
    }
}
