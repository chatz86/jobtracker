package com.example.jobtracker

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearSyncService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("WearSyncService", "Received: ${messageEvent.path}")
        when (messageEvent.path) {
            Config.SYNC_PATH_CONFIG -> {
                val payload = String(messageEvent.data, Charsets.UTF_8)
                Log.d("WearSyncService", "Config sync from phone: $payload")
            }
            Config.SYNC_PATH_ACTIVE -> {
                val payload = String(messageEvent.data, Charsets.UTF_8)
                Log.d("WearSyncService", "Active sync from phone: $payload")
            }
            Config.SYNC_PATH_HISTORY -> {
                val payload = String(messageEvent.data, Charsets.UTF_8)
                Log.d("WearSyncService", "History sync from phone: $payload")
            }
        }
    }
}
