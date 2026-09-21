@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.jobtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PhoneSync.request(this)
        requestNotificationPermissionIfNeeded()
        setContent {
            MaterialTheme {
                PhoneReceiverUI()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check on open: if the phone was away while a job ran long, the alert should
        // still appear without waiting for the next sync from the watch.
        WatchdogNotifier.check(this)
    }

    /**
     * The watchdog posts a phone notification when a job runs long. Android 13+ needs
     * the runtime permission for that, so it is asked for once on first launch.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 200
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PhoneReceiverUI() {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { refreshKey++ }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("com.example.jobtracker.REFRESH"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    val active = remember(refreshKey) { PhonePersistence.getActiveEntry(context) }
    val history = remember(refreshKey) { PhonePersistence.getHistory(context) }
    val wards = remember(refreshKey) { PhonePersistence.getWards(context) }
    val attendees = remember(refreshKey) { PhonePersistence.getAttendees(context) }
    var query by remember { mutableStateOf("") }

    // Free-text filter across everything a record holds, so a ward, a patient or a
    // note can be found without scrolling the whole list.
    val filteredHistory = remember(refreshKey, query) {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) history else history.filter { r ->
            listOf(r.type, r.ward, r.patientName, r.patientId, r.notes, r.attendees.joinToString(" "))
                .any { it.lowercase(Locale.US).contains(q) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("JobTracker", color = Color(0xFF64B5F6)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("ACTIVE", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (active != null && active.ward.isNotBlank()) {
                item { ActiveJobCard(active) }
            } else {
                item {
                    Text("No active job", color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search history") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            item {
                Button(
                    onClick = { CsvExporter.exportAndShare(context, filteredHistory) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = filteredHistory.isNotEmpty()
                ) { Text("Export ${filteredHistory.size} to CSV") }
            }

            item {
                Text("HISTORY (${filteredHistory.size}${if (query.isBlank()) "" else " of ${history.size}"})",
                    fontSize = 11.sp, color = Color.Gray,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }
            if (filteredHistory.isEmpty()) {
                item {
                    Text(if (query.isBlank()) "No history" else "No matches for \"$query\"",
                        color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            items(filteredHistory.asReversed()) { record ->
                HistoryItem(record)
            }

            item {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)) {
                    Text("CONFIG (from watch)", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Locations (${wards.size})", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            if (wards.isEmpty()) {
                                Text("No locations", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
                            } else {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    wards.forEach { ward ->
                                        Text(ward, color = Color.White, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("SO's (${attendees.size})", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            if (attendees.isEmpty()) {
                                Text("No SO's", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
                            } else {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    attendees.forEach { attendee ->
                                        Text(attendee, color = Color.White, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveJobCard(entry: PhoneActiveEntry) {
    // The card is only composed while a job is running, so this ticker keeps the
    // elapsed counter live instead of freezing until the next sync arrives.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(entry.startTime) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val elapsed = if (entry.startTime > 0) now - entry.startTime else 0L
    val hrs = elapsed / 3600000
    val mins = (elapsed % 3600000) / 60000
    val secs = (elapsed % 60000) / 1000

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1A2A1A)).padding(12.dp)
    ) {
        Text(entry.type, fontSize = 16.sp, color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
        Text(entry.ward, fontSize = 14.sp, color = Color.White)
        val attText = (listOf("Chat") + entry.attendees).joinToString(", ")
        Text("SO's: $attText", fontSize = 12.sp, color = Color.LightGray)
        // Seconds are shown on purpose: a ticking clock is visible proof that the
        // phone is receiving live data from the watch (a frozen value means sync
        // stopped). Format matches the watch's timer (mm:ss, then h:mm:ss).
        val runningText = if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
        Text("Running: $runningText", fontSize = 12.sp, color = Color(0xFF4CAF50))
        if (entry.patientName.isNotBlank()) Text("Patient: ${entry.patientName}", fontSize = 11.sp, color = Color.LightGray)
        if (entry.patientId.isNotBlank()) Text("UMRN: ${entry.patientId}", fontSize = 11.sp, color = Color.LightGray)
        if (entry.notes.isNotBlank()) Text("Notes: ${entry.notes}", fontSize = 11.sp, color = Color.LightGray)
    }
}

@Composable
fun HistoryItem(record: PhoneHistoryRecord) {
    // LocalLocale is observable, so the format follows locale changes live.
    val locale = LocalLocale.current.platformLocale
    val fmt = remember(locale) { SimpleDateFormat("dd/MM/yy HH:mm", locale) }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${record.type}: ${record.ward}", fontSize = 13.sp, color = Color.White)
            Text(fmt.format(Date(record.endTime)), fontSize = 11.sp, color = Color.Gray)
        }
        val dur = record.endTime - record.startTime
        if (dur > 0) {
            val hrs = dur / 3600000
            val mins = (dur % 3600000) / 60000
            Text("Duration: ${String.format(Locale.US, "%dh %02dm", hrs, mins)}", fontSize = 11.sp, color = Color.Gray)
        }
        val attText = (listOf("Chat") + record.attendees).joinToString(", ")
        Text("SO's: $attText", fontSize = 11.sp, color = Color.LightGray)
        if (record.patientName.isNotBlank()) Text("Patient: ${record.patientName}", fontSize = 11.sp, color = Color.LightGray)
        if (record.patientId.isNotBlank()) Text("UMRN: ${record.patientId}", fontSize = 11.sp, color = Color.LightGray)
        if (record.notes.isNotBlank()) Text("Notes: ${record.notes}", fontSize = 11.sp, color = Color.LightGray)
    }
}
