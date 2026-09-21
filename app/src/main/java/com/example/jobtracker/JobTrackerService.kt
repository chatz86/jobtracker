package com.example.jobtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity

class JobTrackerService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val periodicHandler = Handler(Looper.getMainLooper())
    private val periodicSync = object : Runnable {
        override fun run() {
            Syncer.syncAll(this@JobTrackerService)
            periodicHandler.postDelayed(this, 30_000)
        }
    }

    companion object {
        const val CHANNEL_ID = "jobtracker_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("TYPE") ?: "Job"
        val ward = intent?.getStringExtra("LOCATION") ?: ""
        val startTime = intent?.getLongExtra("START_TIME", System.currentTimeMillis()) ?: System.currentTimeMillis()

        val notification = buildNotification(type, ward, startTime)
        startForeground(NOTIFICATION_ID, notification)
        // Re-posting on every start would stack duplicate periodic syncs when a new
        // job is started while the service is already running.
        periodicHandler.removeCallbacks(periodicSync)
        periodicHandler.postDelayed(periodicSync, 30_000)
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

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JobTracker::ActiveJob")
        wakeLock?.acquire(8 * 60 * 60 * 1000L) // 8 hours max
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
