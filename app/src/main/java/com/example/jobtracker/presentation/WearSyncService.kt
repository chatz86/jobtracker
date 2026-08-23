package com.example.jobtracker.presentation

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearSyncService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == "/jobtracker/config") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val wards = dataMap.getStringArrayList("wards")
                    val attendees = dataMap.getStringArrayList("attendees")
                    
                    scope.launch {
                        wards?.let { ListPersistence.saveWards(this@WearSyncService, it) }
                        attendees?.let { ListPersistence.saveAttendees(this@WearSyncService, it) }
                        Log.d("WearSyncService", "Config updated from phone: wards=${wards?.size}, attendees=${attendees?.size}")
                    }
                } else if (path == "/jobtracker/clear_history") {
                    scope.launch {
                        ListPersistence.clearHistory(this@WearSyncService)
                        Log.d("WearSyncService", "History cleared from phone")
                    }
                }
            }
        }
    }
}
