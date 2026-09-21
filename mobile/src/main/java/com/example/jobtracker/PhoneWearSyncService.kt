package com.example.jobtracker

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONArray
import org.json.JSONObject

class PhoneWearSyncService : WearableListenerService() {
    override fun onPeerConnected(node: Node) {
        Log.d("PhoneSync", "Peer connected ${node.displayName}, requesting full state")
        PhoneSync.request(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val payload = String(messageEvent.data, Charsets.UTF_8)
        Log.d("PhoneSync", "Received ${messageEvent.path}: ${payload.take(100)}")
        handlePayload(messageEvent.path, payload)
    }

    private fun handlePayload(path: String, payload: String) {
        when (path) {
            Config.SYNC_PATH_CONFIG -> {
                try {
                    val parts = payload.split("||")
                    for (part in parts) {
                        when {
                            part.startsWith("WARDS:") -> {
                                val arr = JSONArray(part.removePrefix("WARDS:"))
                                PhonePersistence.saveWards(this, arr)
                            }
                            part.startsWith("ATTENDEES:") -> {
                                val arr = JSONArray(part.removePrefix("ATTENDEES:"))
                                PhonePersistence.saveAttendees(this, arr)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PhoneSync", "Config parse error", e)
                }
            }
            Config.SYNC_PATH_ACTIVE -> {
                try {
                    if (payload == "null") {
                        PhonePersistence.saveActiveEntry(this, null)
                    } else {
                        PhonePersistence.saveActiveEntry(this, JSONObject(payload))
                    }
                } catch (e: Exception) {
                    Log.e("PhoneSync", "Active parse error", e)
                }
            }
            Config.SYNC_PATH_HISTORY -> {
                try {
                    val arr = JSONArray(payload)
                    PhonePersistence.saveHistory(this, arr)
                } catch (e: Exception) {
                    Log.e("PhoneSync", "History parse error", e)
                }
            }
        }

        // Target our own package explicitly: the receiver registered in MainActivity is
        // RECEIVER_NOT_EXPORTED, and implicit broadcasts to non-exported receivers are
        // rejected (UnsafeImplicitIntentLaunch) on newer platform versions.
        val refreshIntent = Intent("com.example.jobtracker.REFRESH").setPackage(packageName)
        sendBroadcast(refreshIntent)
    }
}