package com.example.jobtracker

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONArray
import org.json.JSONObject

class PhoneWearSyncService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val payload = String(messageEvent.data, Charsets.UTF_8)
        Log.d("PhoneSync", "Received ${messageEvent.path}: ${payload.take(100)}")

        when (messageEvent.path) {
            "/jobtracker/config" -> {
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
            "/jobtracker/active" -> {
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
            "/jobtracker/history" -> {
                try {
                    val arr = JSONArray(payload)
                    PhonePersistence.saveHistory(this, arr)
                } catch (e: Exception) {
                    Log.e("PhoneSync", "History parse error", e)
                }
            }
        }

        // Notify the UI to refresh
        val refreshIntent = android.content.Intent("com.example.jobtracker.REFRESH")
        sendBroadcast(refreshIntent)
    }
}
