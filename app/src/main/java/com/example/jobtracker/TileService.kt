package com.example.jobtracker

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonDefaults
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class TileService : TileService() {

    companion object {
        private const val TAG = "JobTrackerTile"

        fun requestUpdate(context: Context) {
            try {
                TileService.getUpdater(context).requestUpdate(TileService::class.java)
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "tile update failed", t)
            }
        }
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion("2")
                .setTileTimeline(buildTimeline(requestParams.deviceConfiguration))
                .build()
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("2")
                .build()
        )
    }

    private fun buildTimeline(deviceParameters: DeviceParametersBuilders.DeviceParameters): TimelineBuilders.Timeline {
        val now = System.currentTimeMillis()
        // Snapshot the state once: a timeline can hold dozens of entries and every
        // entry renders the same data, so there is no need to hit SharedPreferences
        // (and JSON parsing) once per entry.
        val entry = Persistence.getActiveEntry(this)
        val last = Persistence.getHistory(this).lastOrNull()
        val timeline = TimelineBuilders.Timeline.Builder()

        fun addEntry(start: Long, end: Long) {
            timeline.addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setValidity(
                        TimelineBuilders.TimeInterval.Builder()
                            .setStartMillis(start)
                            .setEndMillis(end)
                            .build()
                    )
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(layout(deviceParameters, start, entry, last))
                            .build()
                    )
                    .build()
            )
        }

        if (entry != null) {
            // Emit entries every 30s so the elapsed counter stays live without waking the phone.
            val horizonMs = 30 * 60 * 1000L // 30 minutes
            var t = now
            while (t < now + horizonMs) {
                addEntry(t, t + 30_000L)
                t += 30_000L
            }
        } else {
            addEntry(now, Long.MAX_VALUE)
        }
        return timeline.build()
    }

    private fun launchAction(): ActionBuilders.Action {
        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(
                        "EXTRA_QUICK_START",
                        ActionBuilders.AndroidBooleanExtra.Builder().setValue(false).build()
                    )
                    .build()
            )
            .build()
    }

    private fun layout(
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        atMillis: Long,
        entry: ActiveEntry?,
        last: HistoryRecord?
    ): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(launchAction())
            .build()

        // A live task draws its own attention: solid red banner and button.
        val bannerColor = if (entry != null) {
            ColorBuilders.argb(0xFFC62828.toInt())
        } else {
            ColorBuilders.argb(0xFF64B5F6.toInt())
        }

        val primaryLabel = Text.Builder(
            this,
            if (entry != null) "TASK RUNNING" else if (last != null) "LAST JOB" else "NO JOBS"
        )
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(bannerColor)
            .build()

        val buttonLabel = when {
            entry != null -> "${entry.type}  ${formatElapsed(atMillis - entry.startTime)}"
            last != null -> "${last.ward}  ${formatElapsed(last.endTime - last.startTime)}"
            else -> "Tap to start"
        }

        val buttonColors = if (entry != null) {
            ButtonColors(
                ColorBuilders.argb(0xFFC62828.toInt()),
                ColorBuilders.argb(0xFFFFFFFF.toInt())
            )
        } else {
            ButtonDefaults.PRIMARY_COLORS
        }

        val secondaryText = when {
            entry != null -> {
                val atts = if (entry.attendees.isEmpty()) "Chat" else "Chat, ${entry.attendees.joinToString(", ")}"
                "${entry.ward}  ·  $atts"
            }
            last != null -> "${last.type} · ${(listOf("Chat") + last.attendees).joinToString(", ")}"
            else -> "Job Tracker"
        }

        val secondaryLabel = Text.Builder(this, secondaryText)
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .build()

        return PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setContent(
                Button.Builder(this, clickable)
                    .setButtonColors(buttonColors)
                    .setSize(DimensionBuilders.dp(120f))
                    .setContentDescription(buttonLabel)
                    .setTextContent(buttonLabel)
                    .build()
            )
            .setPrimaryLabelTextContent(primaryLabel)
            .setSecondaryLabelTextContent(secondaryLabel)
            .build()
    }

    private fun formatElapsed(ms: Long): String {
        val mins = ms / 60000
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}