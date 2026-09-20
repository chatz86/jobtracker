package com.example.jobtracker

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/**
 * Pushes immediate refresh requests to the watch tile and complication so they
 * reflect a started/concluded job without waiting for the periodic updates.
 */
object LiveUpdatesHelper {
    private const val TAG = "LiveUpdates"

    fun notifyDataChanged(context: Context) {
        try {
            TileService.requestUpdate(context)
        } catch (t: Throwable) {
            Log.e(TAG, "tile update failed", t)
        }

        try {
            ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, JobTrackerComplicationService::class.java)
            ).requestUpdateAll()
        } catch (t: Throwable) {
            Log.e(TAG, "complication update failed", t)
        }
    }
}