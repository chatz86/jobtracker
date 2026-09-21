package com.example.jobtracker

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class ActiveEntry(
    val type: String,
    val startTime: Long,
    val ward: String,
    val attendees: List<String>,
    val patientName: String = "",
    val patientId: String = "",
    val notes: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("startTime", startTime)
        put("ward", ward)
        put("attendees", JSONArray(attendees))
        put("patientName", patientName)
        put("patientId", patientId)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): ActiveEntry? = try {
            ActiveEntry(
                type = json.getString("type"),
                startTime = json.getLong("startTime"),
                ward = json.getString("ward"),
                attendees = json.optJSONArray("attendees")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                patientName = json.optString("patientName", ""),
                patientId = json.optString("patientId", ""),
                notes = json.optString("notes", "")
            )
        } catch (e: Exception) {
            Log.e("Persistence", "Failed to parse ActiveEntry", e)
            null
        }
    }
}

data class HistoryRecord(
    val type: String,
    val startTime: Long,
    val endTime: Long,
    val ward: String,
    val attendees: List<String>,
    val patientName: String = "",
    val patientId: String = "",
    val notes: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("startTime", startTime)
        put("endTime", endTime)
        put("ward", ward)
        put("attendees", JSONArray(attendees))
        put("patientName", patientName)
        put("patientId", patientId)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): HistoryRecord? = try {
            HistoryRecord(
                type = json.getString("type"),
                startTime = json.getLong("startTime"),
                endTime = json.getLong("endTime"),
                ward = json.getString("ward"),
                attendees = json.optJSONArray("attendees")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                patientName = json.optString("patientName", ""),
                patientId = json.optString("patientId", ""),
                notes = json.optString("notes", "")
            )
        } catch (e: Exception) {
            Log.e("Persistence", "Failed to parse HistoryRecord", e)
            null
        }
    }
}

object Persistence {
    private const val TAG = "JobTrackerPersistence"
    private const val KEY_WARDS = "wards"
    private const val KEY_ATTENDEES = "attendees"
    private const val KEY_ACTIVE = "active_entry"
    private const val KEY_HISTORY = "history"

    private fun prefs(context: Context) =
        context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)

    // --- Wards ---

    fun getWards(context: Context): List<String> {
        val json = prefs(context).getString(KEY_WARDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "getWards parse error", e)
            emptyList()
        }
    }

    fun saveWards(context: Context, wards: List<String>) {
        val arr = JSONArray()
        wards.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_WARDS, arr.toString()).apply()
        Log.d(TAG, "saveWards: ${wards.size} wards")
        Syncer.syncConfig(context)
    }

    // --- Attendees ---

    fun getAttendees(context: Context): List<String> {
        val json = prefs(context).getString(KEY_ATTENDEES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "getAttendees parse error", e)
            emptyList()
        }
    }

    fun saveAttendees(context: Context, attendees: List<String>) {
        val arr = JSONArray()
        attendees.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_ATTENDEES, arr.toString()).apply()
        Log.d(TAG, "saveAttendees: ${attendees.size} attendees")
        Syncer.syncConfig(context)
    }

    // --- Active Entry ---

    fun getActiveEntry(context: Context): ActiveEntry? {
        val json = prefs(context).getString(KEY_ACTIVE, null) ?: return null
        return try {
            ActiveEntry.fromJson(JSONObject(json))
        } catch (e: Exception) {
            Log.e(TAG, "getActiveEntry parse error", e)
            null
        }
    }

    fun saveActiveEntry(context: Context, entry: ActiveEntry?) {
        if (entry == null) {
            prefs(context).edit().remove(KEY_ACTIVE).apply()
            Log.d(TAG, "saveActiveEntry: cleared")
        } else {
            prefs(context).edit().putString(KEY_ACTIVE, entry.toJson().toString()).apply()
            Log.d(TAG, "saveActiveEntry: ${entry.type} @ ${entry.ward}")
        }
        Syncer.syncActive(context)
        LiveUpdatesHelper.notifyDataChanged(context)
    }

    // --- History ---

    fun getHistory(context: Context): List<HistoryRecord> {
        val json = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { HistoryRecord.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "getHistory parse error", e)
            emptyList()
        }
    }

    fun saveToHistory(context: Context, record: HistoryRecord) {
        val history = getHistory(context).toMutableList()
        history.add(record)
        val limited = history.takeLast(100)
        val arr = JSONArray()
        limited.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
        Log.d(TAG, "saveToHistory: ${record.type} @ ${record.ward}, total=${limited.size}")
        Syncer.syncHistory(context)
        LiveUpdatesHelper.notifyDataChanged(context)
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
        Log.d(TAG, "clearHistory")
        Syncer.syncHistory(context)
        LiveUpdatesHelper.notifyDataChanged(context)
    }

    fun deleteWards(context: Context, toDelete: Set<String>) {
        val filtered = getWards(context).filter { it !in toDelete }
        saveWards(context, filtered)
    }

    fun deleteAttendees(context: Context, toDelete: Set<String>) {
        val filtered = getAttendees(context).filter { it !in toDelete }
        saveAttendees(context, filtered)
    }
}
