package com.example.jobtracker

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.Instant
import java.util.concurrent.TimeUnit

class JobTrackerComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val entry = Persistence.getActiveEntry(this)
        val last = Persistence.getHistory(this).lastOrNull()

        val pendingIntent = PendingIntent.getActivity(
            this,
            request.complicationInstanceId,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("EXTRA_QUICK_START", entry == null)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return when (request.complicationType) {
            ComplicationType.LONG_TEXT -> {
                if (entry != null) {
                    // Live stopwatch: the watch face itself renders the ticking
                    // elapsed time, counting up from the job's start instant.
                    LongTextComplicationData.Builder(
                        text = TimeDifferenceComplicationText.Builder(
                            TimeDifferenceStyle.SHORT_DUAL_UNIT,
                            CountUpTimeReference(Instant.ofEpochMilli(entry.startTime))
                        ).setMinimumTimeUnit(TimeUnit.SECONDS).build(),
                        contentDescription = PlainComplicationText.Builder("Job Tracker: active job").build()
                    ).setTitle(
                        PlainComplicationText.Builder(entry.ward.ifBlank { "JOB ACTIVE" }).build()
                    ).setTapAction(pendingIntent).build()
                } else {
                    val title = if (last != null) "LAST JOB" else "TRACKER"
                    val text = when {
                        last != null -> "${last.ward}  ${formatElapsed(last.endTime - last.startTime)}"
                        else -> "Tap to start"
                    }
                    LongTextComplicationData.Builder(
                        text = PlainComplicationText.Builder(text).build(),
                        contentDescription = PlainComplicationText.Builder("Job Tracker status").build()
                    ).setTitle(
                        PlainComplicationText.Builder(title).build()
                    ).setTapAction(pendingIntent).build()
                }
            }

            else -> {
                if (entry != null) {
                    // Live stopwatch in the compact text.
                    ShortTextComplicationData.Builder(
                        text = TimeDifferenceComplicationText.Builder(
                            TimeDifferenceStyle.STOPWATCH,
                            CountUpTimeReference(Instant.ofEpochMilli(entry.startTime))
                        ).setMinimumTimeUnit(TimeUnit.SECONDS).build(),
                        contentDescription = PlainComplicationText.Builder("Job Tracker: active job").build()
                    ).setTitle(
                        PlainComplicationText.Builder(shortType(entry.type)).build()
                    ).setTapAction(pendingIntent).build()
                } else {
                    val title = if (last != null) "LAST" else "JOB"
                    val text = if (last != null) last.ward else "START"
                    ShortTextComplicationData.Builder(
                        text = PlainComplicationText.Builder(text).build(),
                        contentDescription = PlainComplicationText.Builder("Job Tracker status").build()
                    ).setTitle(
                        PlainComplicationText.Builder(title).build()
                    ).setTapAction(pendingIntent).build()
                }
            }
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("Frankland  1h 23m").build(),
                contentDescription = PlainComplicationText.Builder("Tracker preview").build()
            ).setTitle(
                PlainComplicationText.Builder("JOB ACTIVE").build()
            ).build()

            else -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("1h 23m").build(),
                contentDescription = PlainComplicationText.Builder("Tracker preview").build()
            ).setTitle(
                PlainComplicationText.Builder("JOB").build()
            ).build()
        }.also {
            Log.d("JobTracker", "Complication preview: $type")
        }
    }

    private fun shortType(type: String): String = when (type) {
        "Foot Patrol" -> "FOOT"
        "Vehicle Patrol" -> "VEH"
        else -> "JOB"
    }

    private fun formatElapsed(ms: Long): String {
        val mins = ms / 60000
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}