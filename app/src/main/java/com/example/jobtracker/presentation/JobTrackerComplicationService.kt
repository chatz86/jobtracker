package com.example.jobtracker.presentation

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class JobTrackerComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        Log.d("JobTracker", "Complication Request: ${request.complicationInstanceId}")
        
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_QUICK_START", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            request.complicationInstanceId, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("START").build(),
            contentDescription = PlainComplicationText.Builder("Launch Job Tracker").build()
        )
            .setTapAction(pendingIntent)
            .build()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("START").build(),
            contentDescription = PlainComplicationText.Builder("Tracker Preview").build()
        ).build()
    }
}
