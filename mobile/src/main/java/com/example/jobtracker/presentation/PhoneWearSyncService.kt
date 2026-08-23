package com.example.jobtracker.presentation

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneWearSyncService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == "/jobtracker/history") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val rawHistory = dataMap.getString("history_raw") ?: ""
                    
                    scope.launch {
                        ListPersistence.saveRawHistory(this@PhoneWearSyncService, rawHistory)
                        Log.d("PhoneSync", "History updated from watch")
                    }
                } else if (path == "/jobtracker/active") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val isActive = dataMap.getBoolean("is_active")
                    
                    scope.launch {
                        if (isActive) {
                            val type = dataMap.getString("type") ?: ""
                            val start = dataMap.getLong("start")
                            val ward = dataMap.getString("ward") ?: ""
                            val staff = dataMap.getStringArrayList("staff") ?: emptyList<String>()
                            val patient = dataMap.getString("patient") ?: ""
                            val patientId = dataMap.getString("patientId") ?: ""
                            val notes = dataMap.getString("notes") ?: ""
                            val rawActive = "$type|$start|$ward|${staff.joinToString(",")}|$patient|$patientId|$notes"
                            ListPersistence.saveRawActiveEntry(this@PhoneWearSyncService, rawActive)
                            Log.d("PhoneSync", "Active entry updated from watch")
                        } else {
                            ListPersistence.saveRawActiveEntry(this@PhoneWearSyncService, null)
                            Log.d("PhoneSync", "Active entry cleared from watch")
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d("PhoneSync", "Message received: ${messageEvent.path}")
    }
}
