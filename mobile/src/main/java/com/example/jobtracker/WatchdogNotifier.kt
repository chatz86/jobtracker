package com.example.jobtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Phone-side half of the long-job watchdog. The watch owns the thresholds; they are
 * mirrored here by the config sync, so nothing needs configuring twice. A phone
 * notification is posted when a running job crosses the warning threshold and again
 * at the critical threshold, so a forgotten job is visible even off-wrist.
 */
object WatchdogNotifier {
    private const val TAG = "WatchdogNotifier"
    private const val CHANNEL_ID = "jobtracker_watchdog"
    private const val NOTIFICATION_ID = 42

    fun check(context: Context) {
        try {
            val entry = PhonePersistence.getActiveEntry(context)
            if (entry == null || entry.startTime <= 0L) {
                PhonePersistence.saveLastAlertLevel(context, 0)
                return
            }
            val elapsedH = (System.currentTimeMillis() - entry.startTime) / 3_600_000.0
            val warnH = PhonePersistence.getWarnHours(context)
            var critH = PhonePersistence.getCritHours(context)
            if (critH <= warnH) critH = warnH + 1
            val level = when {
                elapsedH >= critH -> 2
                elapsedH >= warnH -> 1
                else -> 0
            }
            // Only alert on an increase: re-posting on every sync would nag.
            if (level <= PhonePersistence.getLastAlertLevel(context)) return
            PhonePersistence.saveLastAlertLevel(context, level)
            if (level == 0) return
            post(context, entry, level)
        } catch (t: Throwable) {
            Log.e(TAG, "watchdog check failed", t)
        }
    }

    /** Called when a job ends (or is cleared) so the next job starts alerting afresh. */
    fun reset(context: Context) {
        PhonePersistence.saveLastAlertLevel(context, 0)
        getManager(context)?.cancel(NOTIFICATION_ID)
    }

    private fun getManager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    private fun post(context: Context, entry: PhoneActiveEntry, level: Int) {
        val manager = getManager(context) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Long job alerts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Warns when a job has been running unusually long" }
        )

        val elapsedMin = (System.currentTimeMillis() - entry.startTime) / 60_000L
        val label = if (level >= 2) "Still running" else "Running long"
        val minutes = "${elapsedMin / 60}h ${elapsedMin % 60}m"

        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("$label: $minutes")
            .setContentText("${entry.type} at ${entry.ward} — remember to conclude it on the watch")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Android 13+ needs the runtime permission; skip quietly if it was refused.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "POST_NOTIFICATIONS not granted, skipping watchdog notification")
            return
        }
        manager.notify(NOTIFICATION_ID, notification)
    }
}
