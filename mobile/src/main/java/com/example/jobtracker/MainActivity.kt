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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    LaunchedEffect(Unit) {
        PhoneSyncer.requestFullSync(context)
    }

    val active = remember(refreshKey) { PhonePersistence.getActiveEntry(context) }
    val history = remember(refreshKey) { PhonePersistence.getHistory(context) }
    var wards by remember { mutableStateOf(PhonePersistence.getWards(context).toMutableList()) }
    var attendees by remember { mutableStateOf(PhonePersistence.getAttendees(context).toMutableList()) }

    var showAddWard by remember { mutableStateOf(false) }
    var showAddAttendee by remember { mutableStateOf(false) }
    var newWard by remember { mutableStateOf("") }
    var newAttendee by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("JobTracker", color = Color(0xFF64B5F6)) },
            actions = {
                Button(
                    onClick = { refreshKey++; PhoneSyncer.requestFullSync(context) },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A1A1A),
                        contentColor = Color(0xFF64B5F6)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh sync", tint = Color(0xFF64B5F6))
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", fontSize = 14.sp, color = Color(0xFF64B5F6))
                    }
                }
            },
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
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)) {
                    Text("CONFIG", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    
                    // Wards
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Wards (${wards.size})", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Button(onClick = { showAddWard = true; newWard = "" }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add ward", tint = Color(0xFF64B5F6))
                                }
                            }
                            if (wards.isEmpty()) {
                                Text("No wards", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
                            } else {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    wards.forEach { ward ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(ward, color = Color.White, fontSize = 13.sp)
                                            IconButton(onClick = {
                                                wards.remove(ward)
                                                val updatedWards = wards.toList()
                                                PhonePersistence.saveWards(context, JSONArray(updatedWards))
                                                PhoneSyncer.syncConfig(context, updatedWards, attendees)
                                                refreshKey++
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete $ward", tint = Color.Red, modifier = Modifier.width(20.dp).height(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Attendees
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("SO's (${attendees.size})", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Button(onClick = { showAddAttendee = true; newAttendee = "" }) {
                                Icon(Icons.Default.Add, contentDescription = "Add SO", tint = Color(0xFF64B5F6))
                            }
                        }
                        if (attendees.isEmpty()) {
                            Text("No SO's", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
                        } else {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                attendees.forEach { attendee ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(attendee, color = Color.White, fontSize = 13.sp)
                                        androidx.compose.material3.IconButton(onClick = {
                                            attendees.remove(attendee)
                                            val updatedAttendees = attendees.toList()
                                            PhonePersistence.saveAttendees(context, JSONArray(updatedAttendees))
                                            PhoneSyncer.syncConfig(context, wards, updatedAttendees)
                                            refreshKey++
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete $attendee", tint = Color.Red, modifier = Modifier.width(20.dp).height(20.dp))
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

    // DIALOGS
    if (showAddWard) {
        AlertDialog(
            onDismissRequest = { showAddWard = false; newWard = "" },
            title = { Text("Add Ward", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newWard,
                    onValueChange = { newWard = it },
                    label = { Text("Ward name", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newWard.isNotBlank() && !wards.contains(newWard)) {
                        wards.add(newWard)
                        PhonePersistence.saveWards(context, JSONArray(wards))
                        PhoneSyncer.syncConfig(context, wards, attendees)
                        refreshKey++
                    }
                    showAddWard = false
                    newWard = ""
                }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6), contentColor = Color.White)) {
                    Text("Add")
                }
            },
            dismissButton = {
                Button(onClick = { showAddWard = false; newWard = "" }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A), contentColor = Color.Gray)) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddAttendee) {
        AlertDialog(
            onDismissRequest = { showAddAttendee = false; newAttendee = "" },
            title = { Text("Add SO", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newAttendee,
                    onValueChange = { newAttendee = it },
                    label = { Text("SO name", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newAttendee.isNotBlank() && !attendees.contains(newAttendee)) {
                        attendees.add(newAttendee)
                        PhonePersistence.saveAttendees(context, JSONArray(attendees))
                        PhoneSyncer.syncConfig(context, wards, attendees)
                        refreshKey++
                    }
                    showAddAttendee = false
                    newAttendee = ""
                }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6), contentColor = Color.White)) {
                    Text("Add")
                }
            },
            dismissButton = {
                Button(onClick = { showAddAttendee = false; newAttendee = "" }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A), contentColor = Color.Gray)) {
                    Text("Cancel")
                }
            }
        )
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