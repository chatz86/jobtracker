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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PhoneReceiverUI()
            }
        }
    }
}

@Composable
fun PhoneReceiverUI() {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { refreshKey++ }
        }
        context.registerReceiver(receiver, IntentFilter("com.example.jobtracker.REFRESH"), Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val active = remember(refreshKey) { PhonePersistence.getActiveEntry(context) }
    val history = remember(refreshKey) { PhonePersistence.getHistory(context) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("JobTracker", color = Color(0xFF64B5F6)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active Job
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

            // History
            item {
                Text("HISTORY (${history.size})", fontSize = 11.sp, color = Color.Gray,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }
            if (history.isEmpty()) {
                item {
                    Text("No history", color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            items(history.asReversed()) { record ->
                HistoryItem(record)
            }

            // Config
            item {
                val wards = remember(refreshKey) { PhonePersistence.getWards(context) }
                val attendees = remember(refreshKey) { PhonePersistence.getAttendees(context) }
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)) {
                    Text("CONFIG", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("Locations: ${wards.joinToString(", ").ifBlank { "None" }}", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
                    Text("SO's: ${attendees.joinToString(", ").ifBlank { "None" }}", fontSize = 12.sp, color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun ActiveJobCard(entry: PhoneActiveEntry) {
    val elapsed = if (entry.startTime > 0) System.currentTimeMillis() - entry.startTime else 0L
    val hrs = elapsed / 3600000
    val mins = (elapsed % 3600000) / 60000

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1A2A1A)).padding(12.dp)
    ) {
        Text(entry.type, fontSize = 16.sp, color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
        Text(entry.ward, fontSize = 14.sp, color = Color.White)
        val attText = if (entry.attendees.isEmpty()) "Solo" else entry.attendees.joinToString(", ")
        Text("SO's: $attText", fontSize = 12.sp, color = Color.LightGray)
        Text("Running: ${String.format("%d:%02d", hrs, mins)}", fontSize = 12.sp, color = Color(0xFF4CAF50))
        if (entry.patientName.isNotBlank()) Text("Patient: ${entry.patientName}", fontSize = 11.sp, color = Color.LightGray)
        if (entry.patientId.isNotBlank()) Text("UMRN: ${entry.patientId}", fontSize = 11.sp, color = Color.LightGray)
        if (entry.notes.isNotBlank()) Text("Notes: ${entry.notes}", fontSize = 11.sp, color = Color.LightGray)
    }
}

@Composable
fun HistoryItem(record: PhoneHistoryRecord) {
    val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())

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
            Text("Duration: ${String.format("%dh %02dm", hrs, mins)}", fontSize = 11.sp, color = Color.Gray)
        }
        val attText = if (record.attendees.isEmpty()) "Solo" else record.attendees.joinToString(", ")
        Text("SO's: $attText", fontSize = 11.sp, color = Color.LightGray)
        if (record.patientName.isNotBlank()) Text("Patient: ${record.patientName}", fontSize = 11.sp, color = Color.LightGray)
        if (record.patientId.isNotBlank()) Text("UMRN: ${record.patientId}", fontSize = 11.sp, color = Color.LightGray)
        if (record.notes.isNotBlank()) Text("Notes: ${record.notes}", fontSize = 11.sp, color = Color.LightGray)
    }
}
