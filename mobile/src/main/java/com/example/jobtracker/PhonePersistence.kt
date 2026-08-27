package com.example.jobtracker

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class PhoneActiveEntry(
    val type: String = "",
    val startTime: Long = 0L,
    val ward: String = "",
    val attendees: List<String> = emptyList(),
    val patientName: String = "",
    val patientId: String = "",
    val notes: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): PhoneActiveEntry? = try {
            PhoneActiveEntry(
                type = json.optString("type", ""),
                startTime = json.optLong("startTime", 0L),
                ward = json.optString("ward", ""),
                attendees = json.optJSONArray("attendees")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                patientName = json.optString("patientName", ""),
                patientId = json.optString("patientId", ""),
                notes = json.optString("notes", "")
            )
        } catch (e: Exception) { null }
    }
}

data class PhoneHistoryRecord(
    val type: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val ward: String = "",
    val attendees: List<String> = emptyList(),
    val patientName: String = "",
    val patientId: String = "",
    val notes: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): PhoneHistoryRecord? = try {
            PhoneHistoryRecord(
                type = json.optString("type", ""),
                startTime = json.optLong("startTime", 0L),
                endTime = json.optLong("endTime", 0L),
                ward = json.optString("ward", ""),
                attendees = json.optJSONArray("attendees")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                patientName = json.optString("patientName", ""),
                patientId = json.optString("patientId", ""),
                notes = json.optString("notes", "")
            )
        } catch (e: Exception) { null }
    }
}

object PhonePersistence {
    private const val TAG = "PhonePersistence"
    private const val PREFS_NAME = "jobtracker_phone"
    private const val KEY_ACTIVE = "active"
    private const val KEY_HISTORY = "history"
    private const val KEY_WARDS = "wards"
    private const val KEY_ATTENDEES = "attendees"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveEntry(context: Context): PhoneActiveEntry? {
        val json = prefs(context).getString(KEY_ACTIVE, null) ?: return null
        return try { PhoneActiveEntry.fromJson(JSONObject(json)) } catch (e: Exception) { null }
    }

    fun saveActiveEntry(context: Context, json: JSONObject?) {
        if (json == null) prefs(context).edit().remove(KEY_ACTIVE).apply()
        else prefs(context).edit().putString(KEY_ACTIVE, json.toString()).apply()
    }

    fun getHistory(context: Context): List<PhoneHistoryRecord> {
        val json = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { PhoneHistoryRecord.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun saveHistory(context: Context, arr: JSONArray) {
        prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun getWards(context: Context): List<String> {
        val json = prefs(context).getString(KEY_WARDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun saveWards(context: Context, arr: JSONArray) {
        prefs(context).edit().putString(KEY_WARDS, arr.toString()).apply()
    }

    fun getAttendees(context: Context): List<String> {
        val json = prefs(context).getString(KEY_ATTENDEES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun saveAttendees(context: Context, arr: JSONArray) {
        prefs(context).edit().putString(KEY_ATTENDEES, arr.toString()).apply()
    }
}
