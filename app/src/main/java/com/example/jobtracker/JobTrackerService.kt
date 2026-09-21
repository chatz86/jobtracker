package com.example.jobtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity

class JobTrackerService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val periodicHandler = Handler(Looper.getMainLooper())
    private var syncTick = 0
    private var lastWarnLevel = 0
    private var lastCritBuzz = 0L
    private val periodicSync = object : Runnable {
        override fun run() {
            Syncer.syncAll(this@JobTrackerService)
            syncTick++
            val entry = Persistence.getActiveEntry(this@JobTrackerService)
            if (entry != null) {
                val now = System.currentTimeMillis()
                // Configurable reminder: buzz every N minutes unless snoozed or off.
                val reminderMin = Persistence.getReminderMinutes(this@JobTrackerService)
                val snoozed = now < Persistence.getSnoozeUntil(this@JobTrackerService)
                if (!snoozed && reminderMin > 0) {
                    val ticksNeeded = (reminderMin * 60_000L / SYNC_INTERVAL_MS).toInt().coerceAtLeast(1)
                    if (syncTick % ticksNeeded == 0) vibrateReminder()
                }
                checkWatchdog(entry.startTime, now)
            }
            periodicHandler.postDelayed(this, SYNC_INTERVAL_MS)
        }
    }

    companion object {
        const val CHANNEL_ID = "jobtracker_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_CONCLUDE = "com.example.jobtracker.CONCLUDE"
        const val ACTION_SNOOZE = "com.example.jobtracker.SNOOZE_10"
        private const val SYNC_INTERVAL_MS = 30_000L
        private const val SNOOZE_MS = 10 * 60_000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONCLUDE -> {
                concludeFromNotification()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                Persistence.setSnoozeUntil(this, System.currentTimeMillis() + SNOOZE_MS)
                // Re-post so the shade reflects the snoozed state immediately.
                Persistence.getActiveEntry(this)?.let {
                    val n = buildNotification(it.type, it.ward, it.startTime)
                    getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
                }
                return START_STICKY
            }
        }
        val type = intent?.getStringExtra("TYPE") ?: "Job"
        val ward = intent?.getStringExtra("LOCATION") ?: ""
        val startTime = intent?.getLongExtra("START_TIME", System.currentTimeMillis()) ?: System.currentTimeMillis()

        val notification = buildNotification(type, ward, startTime)
        startForeground(NOTIFICATION_ID, notification)
        // Re-posting on every start would stack duplicate periodic syncs when a new
        // job is started while the service is already running.
        periodicHandler.removeCallbacks(periodicSync)
        syncTick = 0
        lastWarnLevel = 0
        lastCritBuzz = 0L
        Persistence.clearSnooze(this)
        periodicHandler.postDelayed(periodicSync, SYNC_INTERVAL_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        periodicHandler.removeCallbacks(periodicSync)
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Job Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active job tracking" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(type: String, ward: String, startTime: Long): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.jobtracker.OPEN_ACTIVE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "$type active"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(ward)
            // White-on-transparent monochrome icon, as Wear expects for notifications.
            .setSmallIcon(R.drawable.ic_job_active)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)

        val concludeIntent = Intent(this, JobTrackerService::class.java).apply { action = ACTION_CONCLUDE }
        val concludePi = PendingIntent.getService(
            this, 1, concludeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = Intent(this, JobTrackerService::class.java).apply { action = ACTION_SNOOZE }
        val snoozePi = PendingIntent.getService(
            this, 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozed = System.currentTimeMillis() < Persistence.getSnoozeUntil(this)
        val elapsedMin = ((System.currentTimeMillis() - startTime) / 60_000L).coerceAtLeast(0)
        val content = if (snoozed) "$ward · snoozed 10 min" else contentForElapsed(ward, elapsedMin)
        builder.setContentText(content)
            .addAction(0, "Conclude", concludePi)
            .addAction(0, "Snooze 10m", snoozePi)

        // Surface the running job on the watch face and in recents via the Ongoing
        // Activity API so it can be reopened from anywhere on the device.
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_job_active)
            .setTitle(title)
            .setTouchIntent(pendingIntent)
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    private fun contentForElapsed(ward: String, elapsedMin: Long): String {
        if (elapsedMin < 60) return ward
        val h = elapsedMin / 60
        val m = elapsedMin % 60
        val critH = Persistence.getCritHours(this).coerceAtLeast(1)
        return if (elapsedMin >= critH * 60) "⚠ ${h}h ${m}m — still running? · $ward" else "$ward · ${h}h ${m}m"
    }

    /**
     * Closes the active job straight from the notification shade (no Conclude
     * screen, so patient/notes default to empty). Mirrors MainActivity.saveAndReturn
     * minus the UI state.
     */
    private fun concludeFromNotification() {
        val entry = Persistence.getActiveEntry(this) ?: run { stopSelf(); return }
        val now = System.currentTimeMillis()
        Persistence.saveToHistory(
            this,
            HistoryRecord(entry.type, entry.startTime, now, entry.ward, entry.attendees, entry.patientName, entry.patientId, entry.notes)
        )
        Persistence.saveActiveEntry(this, null)
        Persistence.clearSnooze(this)
        Syncer.syncHistory(this)
        Syncer.syncActive(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Long-job watchdog: escalating buzz once past the warning threshold, then a
     * critical pattern at most every 30 min past the critical threshold. Thresholds
     * come from Settings (hours) so a "forgot to conclude" job can't sit silently.
     */
    private fun checkWatchdog(startTime: Long, now: Long) {
        val warnH = Persistence.getWarnHours(this).coerceAtLeast(1)
        var critH = Persistence.getCritHours(this).coerceAtLeast(1)
        if (critH <= warnH) critH = warnH + 1
        val elapsedH = (now - startTime) / 3_600_000.0
        val level = when {
            elapsedH >= critH -> 2
            elapsedH >= warnH -> 1
            else -> 0
        }
        if (level == 1 && lastWarnLevel == 0) {
            // Warning: long pattern, once per crossing.
            vibratePattern(longArrayOf(0, 500, 200, 500, 200, 1000))
        } else if (level == 2 && now - lastCritBuzz >= 30 * 60_000L) {
            // Critical: harsher triple-buzz, at most every 30 min.
            vibratePattern(longArrayOf(0, 700, 200, 700, 200, 700, 400, 1200))
            lastCritBuzz = now
            // Refresh the notification text so the ⚠ elapsed line appears.
            Persistence.getActiveEntry(this)?.let {
                val n = buildNotification(it.type, it.ward, it.startTime)
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
            }
        }
        lastWarnLevel = level
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JobTracker::ActiveJob")
        wakeLock?.acquire(8 * 60 * 60 * 1000L) // 8 hours max
    }

    /**
     * Short double-buzz nudge so a forgotten active job is noticed. Works with the
     * screen off; only ever called while an active entry exists, and the service is
     * stopped when a job is concluded. VIBRATE is a normal (install-time) permission
     * and VibrationEffect has existed since API 26, so no version guard is needed
     * beyond the VibratorManager lookup (API 31+).
     */
    private fun vibrateReminder() {
        vibratePattern(longArrayOf(0, 250, 150, 250))
    }

    private fun vibratePattern(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
