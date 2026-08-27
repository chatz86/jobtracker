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
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class TileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion("2")
                .setTileTimeline(
                    TimelineBuilders.Timeline.fromLayoutElement(
                        layout(this, requestParams.deviceConfiguration)
                    )
                )
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

    private fun layout(context: Context, deviceParameters: DeviceParametersBuilders.DeviceParameters): LayoutElementBuilders.LayoutElement {
        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(context.packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(
                        "EXTRA_QUICK_START",
                        ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build()
                    )
                    .build()
            )
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(launchAction)
            .build()

        val entry = Persistence.getActiveEntry(context)
        val history = Persistence.getHistory(context)
        val lastJob = history.lastOrNull()

        val label = when {
            entry != null -> "${entry.type} - ${entry.ward}"
            lastJob != null -> "Last: ${lastJob.ward}"
            else -> "START"
        }

        val subtitle = when {
            entry != null -> {
                val elapsed = System.currentTimeMillis() - entry.startTime
                val hrs = elapsed / 3600000
                val mins = (elapsed % 3600000) / 60000
                "${String.format("%02d:%02d", hrs, mins)} running"
            }
            history.isNotEmpty() -> "${history.size} jobs logged"
            else -> "Job Tracker"
        }

        return PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setContent(
                Button.Builder(context, clickable)
                    .setButtonColors(ButtonDefaults.PRIMARY_COLORS)
                    .setSize(DimensionBuilders.dp(120f))
                    .setContentDescription(label)
                    .setTextContent(label)
                    .build()
            )
            .setPrimaryLabelTextContent(
                Text.Builder(context, subtitle)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(ColorBuilders.argb(0xFF64B5F6.toInt()))
                    .build()
            )
            .build()
    }
}
