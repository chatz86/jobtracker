package com.example.jobtracker.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JobTrackerPhoneUI()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobTrackerPhoneUI() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by ListPersistence.getHistory(context).collectAsState(initial = emptyList())
    val wards by ListPersistence.getWards(context).collectAsState(initial = emptyList())
    val attendees by ListPersistence.getAttendees(context).collectAsState(initial = emptyList())
    val activeEntry by ListPersistence.getActiveEntry(context).collectAsState(initial = null)
    
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JobTracker Dashboard") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            activeEntry?.let { entry ->
                ActiveJobCard(entry)
            }

            Text(
                "Recent History",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(history) { record ->
                    HistoryItem(record)
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            wards = wards,
            attendees = attendees,
            onDismiss = { showSettings = false },
            onUpdateWards = { newWards -> scope.launch { ListPersistence.saveWards(context, newWards) } },
            onUpdateAttendees = { newAttendees -> scope.launch { ListPersistence.saveAttendees(context, newAttendees) } }
        )
    }
}

@Composable
fun ActiveJobCard(entry: ListPersistence.ActiveEntry) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "ACTIVE: ${entry.type}", 
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                entry.ward, 
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Attendees: ${entry.staff.joinToString(", ")}", 
                style = MaterialTheme.typography.bodyMedium
            )
            if (entry.patient.isNotBlank()) {
                Text(
                    "Patient: ${entry.patient}", 
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (entry.patientId.isNotBlank()) {
                Text(
                    "UMRN: ${entry.patientId}", 
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (entry.notes.isNotBlank()) {
                Text(
                    "Notes: ${entry.notes}", 
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Commenced: ${timeFormat.format(Date(entry.start))}", 
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "Date: ${dateFormat.format(Date(entry.start))}", 
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun HistoryItem(record: ListPersistence.HistoryRecord) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(record.type, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(dateFormat.format(Date(record.start)), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                record.ward, 
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Attendees: ${record.staff.joinToString(", ")}", 
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (record.notes.isNotBlank()) {
                Text(
                    "Notes: ${record.notes}", 
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (record.patient.isNotBlank() || record.patientId.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (record.patient.isNotBlank()) {
                    Text("Patient: ${record.patient}", style = MaterialTheme.typography.bodyMedium)
                }
                if (record.patientId.isNotBlank()) {
                    Text("UMRN: ${record.patientId}", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Commenced: ${timeFormat.format(Date(record.start))}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "Concluded: ${timeFormat.format(Date(record.end))}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "Date: ${dateFormat.format(Date(record.start))}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun SettingsDialog(
    wards: List<String>,
    attendees: List<String>,
    onDismiss: () -> Unit,
    onUpdateWards: (List<String>) -> Unit,
    onUpdateAttendees: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(0) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingPinAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showPinDialog) {
        PinEntryDialog(
            onDismiss = { showPinDialog = false },
            onCorrectPin = {
                showPinDialog = false
                pendingPinAction?.invoke()
                pendingPinAction = null
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("App Configuration") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TabRow(selectedTabIndex = activeTab) {
                    Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                        Text("Wards", modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                        Text("Attendees", modifier = Modifier.padding(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val currentList = if (activeTab == 0) wards else attendees
                val onUpdate = if (activeTab == 0) onUpdateWards else onUpdateAttendees
                
                ListEditor(items = currentList, onUpdate = { newList ->
                    pendingPinAction = { onUpdate(newList) }
                    showPinDialog = true
                })
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        pendingPinAction = { scope.launch { ListPersistence.clearHistory(context) } }
                        showPinDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All History", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    )
}

@Composable
fun PinEntryDialog(onDismiss: () -> Unit, onCorrectPin: () -> Unit) {
    var pinInput by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Deletion PIN") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = pinInput.replace(Regex("."), "*"),
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                val keys = listOf("123", "456", "789", "C0<")
                keys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { char ->
                            TextButton(
                                onClick = {
                                    when (char) {
                                        'C' -> pinInput = ""
                                        '<' -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                        else -> if (pinInput.length < 4) pinInput += char
                                    }
                                    if (pinInput == Configuration.DELETION_PIN) {
                                        onCorrectPin()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(char.toString(), style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ListEditor(items: List<String>, onUpdate: (List<String>) -> Unit) {
    var newItem by remember { mutableStateOf("") }
    
    val onAdd = {
        if (newItem.isNotBlank()) {
            onUpdate(items + newItem)
            newItem = ""
        }
    }

    Column {
        Row {
            TextField(
                value = newItem,
                onValueChange = { newItem = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add new...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                singleLine = true
            )
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
            items(items) { item ->
                ListItem(
                    headlineContent = { Text(item) },
                    trailingContent = {
                        TextButton(onClick = { onUpdate(items - item) }) {
                            Text("Remove", color = Color.Red)
                        }
                    }
                )
            }
        }
    }
}
