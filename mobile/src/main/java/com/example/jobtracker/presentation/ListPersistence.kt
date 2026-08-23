package com.example.jobtracker.presentation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jobtracker_prefs")

object ListPersistence {
    private val WARDS_KEY = stringPreferencesKey("wards")
    private val ATTENDEES_KEY = stringPreferencesKey("attendees")
    private val ACTIVE_ENTRY_KEY = stringPreferencesKey("active_entry")
    private val HISTORY_KEY = stringPreferencesKey("history")

    data class ActiveEntry(val type: String, val start: Long, val ward: String, val staff: List<String>, val patient: String = "", val patientId: String = "", val notes: String = "")
    data class HistoryRecord(val type: String, val start: Long, val end: Long, val ward: String, val staff: List<String>, val patient: String = "", val patientId: String = "", val notes: String = "")

    fun getActiveEntry(context: Context): Flow<ActiveEntry?> {
        return context.dataStore.data.map { preferences ->
            val data = preferences[ACTIVE_ENTRY_KEY] ?: return@map null
            val parts = data.split("|")
            if (parts.size < 4) return@map null
            if (parts[0] != "Job" && parts[0] != "Patrol") return@map null // Validation
            ActiveEntry(
                type = parts[0],
                start = parts[1].toLongOrNull() ?: 0L,
                ward = parts[2],
                staff = parts[3].split(",").filter { it.isNotBlank() },
                patient = if (parts.size > 4) parts[4] else "",
                patientId = if (parts.size > 5) parts[5] else "",
                notes = if (parts.size > 6) parts[6] else ""
            )
        }
    }

    suspend fun saveActiveEntry(context: Context, entry: ActiveEntry?) {
        context.dataStore.edit { preferences ->
            if (entry == null) {
                preferences.remove(ACTIVE_ENTRY_KEY)
            } else {
                preferences[ACTIVE_ENTRY_KEY] = "${entry.type}|${entry.start}|${entry.ward}|${entry.staff.joinToString(",")}|${entry.patient}|${entry.patientId}|${entry.notes}"
            }
        }
    }

    suspend fun saveRawActiveEntry(context: Context, rawActive: String?) {
        context.dataStore.edit { preferences ->
            if (rawActive == null) {
                preferences.remove(ACTIVE_ENTRY_KEY)
            } else {
                preferences[ACTIVE_ENTRY_KEY] = rawActive
            }
        }
    }

    fun getHistory(context: Context): Flow<List<HistoryRecord>> {
        return context.dataStore.data.map { preferences ->
            preferences[HISTORY_KEY]?.split("||")?.filter { it.isNotBlank() }?.mapNotNull { record ->
                val parts = record.split("|")
                if (parts.size < 5) return@mapNotNull null
                if (parts[0] != "Job" && parts[0] != "Patrol") return@mapNotNull null // Validation
                HistoryRecord(
                    type = parts[0],
                    start = parts[1].toLongOrNull() ?: 0L,
                    end = parts[2].toLongOrNull() ?: 0L,
                    ward = parts[3],
                    staff = parts[4].split(",").filter { it.isNotBlank() },
                    patient = if (parts.size > 5) parts[5] else "",
                    patientId = if (parts.size > 6) parts[6] else "",
                    notes = if (parts.size > 7) parts[7] else ""
                )
            }?.reversed() ?: emptyList()
        }
    }

    suspend fun saveRawHistory(context: Context, rawHistory: String) {
        context.dataStore.edit { preferences ->
            preferences[HISTORY_KEY] = rawHistory
        }
    }

    suspend fun saveToHistory(context: Context, record: HistoryRecord) {
        context.dataStore.edit { preferences ->
            val currentHistory = preferences[HISTORY_KEY] ?: ""
            val recordString = "${record.type}|${record.start}|${record.end}|${record.ward}|${record.staff.joinToString(",")}|${record.patient}|${record.patientId}|${record.notes}"
            // Keep only last 50 records
            val historyList = currentHistory.split("||").filter { it.isNotBlank() }
            val newHistory = (historyList + recordString).takeLast(50).joinToString("||")
            preferences[HISTORY_KEY] = newHistory
            
            // Sync all history to phone
            syncHistoryToPhone(context, newHistory)
        }
    }

    private fun syncHistoryToPhone(context: Context, historyData: String) {
        val request = PutDataMapRequest.create("/jobtracker/history").apply {
            dataMap.putString("history_raw", historyData)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener { android.util.Log.d("JobTrackerSync", "History synced to phone") }
            .addOnFailureListener { android.util.Log.e("JobTrackerSync", "Failed to sync history: ${it.message}") }
    }

    suspend fun clearHistory(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(HISTORY_KEY)
        }
        // Send clear command to watch
        syncClearHistoryToWatch(context)
    }

    private fun syncClearHistoryToWatch(context: Context) {
        val request = PutDataMapRequest.create("/jobtracker/clear_history").apply {
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener { android.util.Log.d("JobTrackerSync", "Clear history command sent to watch") }
    }

    fun getWards(context: Context): Flow<List<String>> {
        return context.dataStore.data.map { preferences ->
            preferences[WARDS_KEY]?.split("|")?.filter { it.isNotBlank() } ?: Configuration.wards
        }
    }

    fun getAttendees(context: Context): Flow<List<String>> {
        return context.dataStore.data.map { preferences ->
            val attendeesString = preferences[ATTENDEES_KEY]
            attendeesString?.split("|")?.filter { it.isNotBlank() } ?: Configuration.attendees
        }
    }

    suspend fun saveWards(context: Context, wards: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[WARDS_KEY] = wards.joinToString("|")
            syncConfigToWatch(context, wards, null)
        }
    }

    suspend fun saveAttendees(context: Context, attendees: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[ATTENDEES_KEY] = attendees.joinToString("|")
            syncConfigToWatch(context, null, attendees)
        }
    }

    private fun syncConfigToWatch(context: Context, wards: List<String>?, attendees: List<String>?) {
        scope.launch {
            val currentWards = wards ?: getWards(context).firstOrNull() ?: emptyList()
            val currentAttendees = attendees ?: getAttendees(context).firstOrNull() ?: emptyList()
            
            val request = PutDataMapRequest.create("/jobtracker/config").apply {
                dataMap.putStringArrayList("wards", ArrayList(currentWards))
                dataMap.putStringArrayList("attendees", ArrayList(currentAttendees))
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request)
                .addOnSuccessListener { android.util.Log.d("JobTrackerSync", "Config synced to watch") }
                .addOnFailureListener { android.util.Log.e("JobTrackerSync", "Failed to sync config: ${it.message}") }
        }
    }
    
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
}
