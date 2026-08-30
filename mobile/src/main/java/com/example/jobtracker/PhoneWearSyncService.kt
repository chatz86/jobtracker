package com.example.jobtracker

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONArray
import org.json.JSONObject

class PhoneWearSyncService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val payload = String(messageEvent.data, Charsets.UTF_8)
        Log.d("PhoneSync", "Received ${messageEvent.path}: ${payload.take(100)}")
        handlePayload(messageEvent.path, payload)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            val path = item.uri.path ?: continue
            val payload = DataMapItem.fromDataItem(item).dataMap.getString("payload") ?: continue
            Log.d("PhoneSync", "DataChanged $path: ${payload.take(100)}")
            handlePayload(path, payload)
        }
    }

    private fun handlePayload(path: String, payload: String) {
        when (path) {
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

        val refreshIntent = Intent("com.example.jobtracker.REFRESH")
        sendBroadcast(refreshIntent)
    }
}
