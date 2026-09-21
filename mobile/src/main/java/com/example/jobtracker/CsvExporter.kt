package com.example.jobtracker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns the mirrored history into a CSV a clinician can hand over or keep. The file
 * is written to the app cache and shared through a FileProvider, so no storage
 * permission is needed and nothing is left behind outside the app sandbox.
 */
object CsvExporter {
    private const val TAG = "CsvExporter"

    private val COLUMNS = listOf(
        "type", "start", "end", "duration_minutes", "location", "attendees",
        "patient_name", "patient_id", "notes"
    )

    fun exportAndShare(context: Context, records: List<PhoneHistoryRecord>) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "jobtracker-$stamp.csv")
            file.writeText(buildCsv(records))

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "JobTracker history ($stamp)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "Export history").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Log.d(TAG, "Exported ${records.size} records to ${file.name}")
        } catch (t: Throwable) {
            Log.e(TAG, "export failed", t)
        }
    }

    fun buildCsv(records: List<PhoneHistoryRecord>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder(COLUMNS.joinToString(",")).append('\n')
        records.forEach { r ->
            val durationMin = if (r.endTime > r.startTime) (r.endTime - r.startTime) / 60_000 else 0
            val cells = listOf(
                r.type,
                fmt.format(Date(r.startTime)),
                fmt.format(Date(r.endTime)),
                durationMin.toString(),
                r.ward,
                (listOf("Chat") + r.attendees).joinToString("; "),
                r.patientName,
                r.patientId,
                r.notes
            )
            sb.append(cells.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
