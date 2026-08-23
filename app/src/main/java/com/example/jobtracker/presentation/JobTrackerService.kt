package com.example.jobtracker.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.jobtracker.R

class JobTrackerService : Service() {
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): JobTrackerService = this@JobTrackerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("TYPE") ?: "Job"
        val location = intent?.getStringExtra("LOCATION") ?: "Unknown"

        Log.d("JobTrackerService", "Starting Service for $type at $location")
        
        createNotificationChannel()
        val notification = createNotification(type, location)
        
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active Job Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active job or patrol status"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(type: String, location: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val ongoingActivityStatus = Status.Builder()
            .addTemplate("Active $type at $location")
            .build()

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext, NOTIFICATION_ID, 
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.splash_icon)
                .setContentTitle("Job Tracker")
                .setContentText("Active $type at $location")
        )
            .setAnimatedIcon(R.drawable.splash_icon)
            .setStaticIcon(R.drawable.splash_icon)
            .setTouchIntent(pendingIntent)
            .setStatus(ongoingActivityStatus)
            .build()

        ongoingActivity.apply(applicationContext)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.splash_icon)
            .setContentTitle("Job Tracker")
            .setContentText("Active $type at $location")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        Log.d("JobTrackerService", "Service Destroyed")
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "job_tracker_channel"
    }
}
