package com.example.jobtracker.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.jobtracker.presentation.theme.JobTrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : androidx.fragment.app.FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {
    private var isQuickStartTrigger = mutableStateOf(value = false)
    private var isAmbientState = mutableStateOf(value = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AmbientModeSupport.attach(this)
        
        val quickStart = intent.getBooleanExtra("EXTRA_QUICK_START", false)
        Log.d("JobTracker", "onCreate: quickStart=$quickStart")
        isQuickStartTrigger.value = quickStart
        setContent {
            JobTrackerApp(isQuickStartTrigger, isAmbientState)
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = object : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            super.onEnterAmbient(ambientDetails)
            isAmbientState.value = true
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            isAmbientState.value = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("EXTRA_QUICK_START", false)) {
            isQuickStartTrigger.value = true
        }
    }
}

@Composable
fun JobTrackerApp(quickStartTrigger: MutableState<Boolean>, isAmbient: MutableState<Boolean>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Request permissions on launch
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            Log.d("JobTracker", "Permission ${it.key} granted: ${it.value}")
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (neededPermissions.isNotEmpty()) {
            multiplePermissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    // Collect dynamic lists from DataStore
    val dynamicWards by ListPersistence.getWards(context).collectAsState(initial = Configuration.wards)
    val dynamicAttendees by ListPersistence.getAttendees(context).collectAsState(initial = Configuration.attendees)
    val activeEntryPersisted by ListPersistence.getActiveEntry(context).collectAsState(initial = null)
    val history by ListPersistence.getHistory(context).collectAsState(initial = emptyList())

    // States: IDLE, VOICE_INIT, VOICE_TYPE, VOICE_WARD, VOICE_STAFF, ACTIVE, VOICE_PATIENT_NAME, VOICE_PATIENT_ID, VOICE_NOTES, SUMMARY, MANAGE_LISTS, VOICE_ADD_WARD, VOICE_ADD_STAFF, HISTORY, PIN_ENTRY, INPUT_KEYBOARD
    var appState by remember { mutableStateOf("IDLE") }
    var keyboardTarget by remember { mutableStateOf("") }
    var keyboardInputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    var nextStateAfterPin by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var autoVoiceEnabled by remember { mutableStateOf(false) }

    // Captured Data
    var entryType by remember { mutableStateOf("Job") }
    var startTime by remember { mutableLongStateOf(0L) }
    var finishTime by remember { mutableLongStateOf(0L) }
    var selectedWard by remember { mutableStateOf("") }
    var selectedAttendees by remember { mutableStateOf(listOf<String>()) }
    var patientName by remember { mutableStateOf("") }
    var patientId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    var pinInput by remember { mutableStateOf("") }
    
    // For hybrid multi-select
    var tempSelectedStaff by remember { mutableStateOf(setOf<String>()) }

    // Restore active state
    LaunchedEffect(activeEntryPersisted) {
        if (activeEntryPersisted != null && appState == "IDLE") {
            entryType = activeEntryPersisted!!.type
            startTime = activeEntryPersisted!!.start
            selectedWard = activeEntryPersisted!!.ward
            selectedAttendees = activeEntryPersisted!!.staff
            patientName = activeEntryPersisted!!.patient
            patientId = activeEntryPersisted!!.patientId
            notes = activeEntryPersisted!!.notes
            appState = "ACTIVE"
        }
    }

    // Persist active state and manage service
    LaunchedEffect(appState, entryType, startTime, selectedWard, selectedAttendees, patientName, patientId, notes) {
        if (appState == "ACTIVE") {
            ListPersistence.saveActiveEntry(context, ListPersistence.ActiveEntry(entryType, startTime, selectedWard, selectedAttendees, patientName, patientId, notes))
            
            // Start Foreground Service
            val serviceIntent = Intent(context, JobTrackerService::class.java).apply {
                putExtra("TYPE", entryType)
                putExtra("LOCATION", selectedWard)
                putExtra("START_TIME", startTime)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            
        } else if (appState == "SUMMARY" || appState == "IDLE") {
            ListPersistence.saveActiveEntry(context, null)
            
            // Stop Foreground Service
            val serviceIntent = Intent(context, JobTrackerService::class.java)
            context.stopService(serviceIntent)
        }
    }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    // Handle Quick Start Trigger from Intent
    LaunchedEffect(quickStartTrigger.value) {
        if (quickStartTrigger.value) {
            entryType = "Job"
            selectedWard = ""
            selectedAttendees = emptyList()
            patientName = ""
            patientId = ""
            notes = ""
            startTime = 0L
            finishTime = 0L
            tempSelectedStaff = emptySet()
            autoVoiceEnabled = true
            appState = "VOICE_INIT"
            quickStartTrigger.value = false // Reset
        }
    }

    // Voice Input Handler
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            when (appState) {
                "VOICE_TYPE" -> {
                    entryType = if (spokenText.contains("patrol", ignoreCase = true)) "Patrol" else "Job"
                    appState = "VOICE_WARD"
                }
                "VOICE_WARD" -> {
                    selectedWard = fuzzyMatch(spokenText, dynamicWards)
                    appState = "VOICE_STAFF"
                }
                "VOICE_STAFF" -> {
                    selectedAttendees = fuzzyMatchList(spokenText, dynamicAttendees)
                    tempSelectedStaff = selectedAttendees.toSet()
                    appState = "ACTIVE"
                }
                "VOICE_PATIENT_NAME" -> {
                    patientName = spokenText
                    appState = "VOICE_PATIENT_ID"
                }
                "VOICE_PATIENT_ID" -> {
                    patientId = spokenText
                    appState = "VOICE_NOTES"
                }
                "VOICE_NOTES" -> {
                    notes = spokenText
                    appState = "SUMMARY"
                }
                "VOICE_ADD_WARD" -> {
                    if (spokenText.isNotBlank()) {
                        scope.launch {
                            ListPersistence.saveWards(context, dynamicWards + spokenText.trim())
                        }
                    }
                    appState = "MANAGE_LISTS"
                }
                "VOICE_ADD_STAFF" -> {
                    if (spokenText.isNotBlank()) {
                        scope.launch {
                            ListPersistence.saveAttendees(context, dynamicAttendees + spokenText.trim())
                        }
                    }
                    appState = "MANAGE_LISTS"
                }
            }
        }
    }

    LaunchedEffect(appState) {
        Log.d("JobTracker", "Current State: $appState, AutoVoice: $autoVoiceEnabled")
        if (appState == "SUMMARY") {
            scope.launch {
                // Final save to local history with all details
                ListPersistence.saveToHistory(
                    context,
                    ListPersistence.HistoryRecord(entryType, startTime, finishTime, selectedWard, selectedAttendees, patientName, patientId, notes)
                )
            }
        }
        
        when (appState) {
            "VOICE_INIT" -> {
                delay(500.milliseconds)
                startTime = System.currentTimeMillis()
                appState = "VOICE_TYPE"
            }
        }
    }

    JobTrackerTheme {
        // Keep screen on during active tracking
        LaunchedEffect(appState, isAmbient.value) {
            val activity = context as? Activity
            if (appState == "ACTIVE" && !isAmbient.value) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        AppScaffold(
            modifier = Modifier.fillMaxSize().background(if (isAmbient.value) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            ScreenScaffold(
                scrollState = listState,
                timeText = { if (!isAmbient.value) TimeText() }
            ) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        appState == "VOICE_TYPE" -> {
                            item { 
                                Text(
                                    "Job Tracker", 
                                    color = Color(0xFF64B5F6), 
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) 
                            }
                            item { ListHeader { Text("Select Type", color = Color.White) } }
                            item {
                                Button(
                                    onClick = { entryType = "Job"; appState = "VOICE_WARD" },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Job") }
                            }
                            item {
                                Button(
                                    onClick = { entryType = "Patrol"; appState = "VOICE_WARD" },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Patrol") }
                            }
                            item {
                                Button(
                                    onClick = { launchVoiceIntent(voiceLauncher, "Job or Patrol?") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🎤 Voice")
                                }
                            }
                        }

                        appState == "VOICE_WARD" -> {
                            item { 
                                Text(
                                    entryType, 
                                    color = Color(0xFF64B5F6), 
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) 
                            }
                            item { ListHeader { Text("Select Location", color = Color.White) } }
                            item {
                                Button(
                                    onClick = { launchVoiceIntent(voiceLauncher, "Which Location?") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🎤 Voice")
                                }
                            }
                            item {
                                Button(
                                    onClick = { 
                                        keyboardTarget = "VOICE_WARD"
                                        keyboardInputText = ""
                                        appState = "INPUT_KEYBOARD"
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("⌨️ Keyboard")
                                }
                            }
                            items(dynamicWards) { ward ->
                                Button(
                                    onClick = { selectedWard = ward; appState = "VOICE_STAFF" },
                                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec)
                                ) { Text(ward) }
                            }
                        }

                        appState == "VOICE_STAFF" -> {
                            item { 
                                Text(
                                    entryType, 
                                    color = Color(0xFF64B5F6), 
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) 
                            }
                            item { ListHeader { Text("Select Attendees", color = Color.White) } }
                            item {
                                Button(
                                    onClick = { selectedAttendees = tempSelectedStaff.toList(); appState = "ACTIVE" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2E7D32),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Confirm (${tempSelectedStaff.size})") }
                            }
                            item {
                                Button(
                                    onClick = { launchVoiceIntent(voiceLauncher, "Which Staff?") },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("🎤 Voice") }
                            }
                            item {
                                Button(
                                    onClick = { 
                                        keyboardTarget = "VOICE_STAFF"
                                        keyboardInputText = ""
                                        appState = "INPUT_KEYBOARD"
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("⌨️ Keyboard") }
                            }
                            items(dynamicAttendees) { attendee ->
                                val isSelected = tempSelectedStaff.contains(attendee)
                                Button(
                                    onClick = {
                                        tempSelectedStaff = if (isSelected) tempSelectedStaff - attendee else tempSelectedStaff + attendee
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF64B5F6) else Color(0xFF212121),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec)
                                ) { Text(attendee) }
                            }
                        }

                        appState.startsWith("VOICE_") && appState != "VOICE_INIT" -> {
                            val prompt = when (appState) {
                                "VOICE_PATIENT_NAME" -> "Patient Name?"
                                "VOICE_PATIENT_ID" -> "Patient UMRN?"
                                "VOICE_NOTES" -> "Extra Notes?"
                                "VOICE_ADD_WARD" -> "Say Location Name"
                                "VOICE_ADD_STAFF" -> "Say Attendee Name"
                                else -> "Speak Now"
                            }
                            item {
                                ListHeader {
                                    Text(
                                        text = "Select Input",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF64B5F6)
                                    )
                                }
                            }
                            item {
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            item { Spacer(modifier = Modifier.height(12.dp)) }
                            item {
                                Button(
                                    onClick = { launchVoiceIntent(voiceLauncher, prompt) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("🎤 Voice") }
                            }
                            item {
                                Button(
                                    onClick = { 
                                        keyboardTarget = appState
                                        keyboardInputText = ""
                                        appState = "INPUT_KEYBOARD"
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("⌨️ Keyboard") }
                            }
                            if (appState == "VOICE_PATIENT_NAME" || appState == "VOICE_PATIENT_ID" || appState == "VOICE_NOTES") {
                                item {
                                    Button(
                                        onClick = { 
                                            appState = if (appState == "VOICE_PATIENT_ID") "VOICE_NOTES"
                                            else "SUMMARY"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Skip") }
                                }
                            }
                        }

                        appState == "IDLE" -> {
                            item {
                                ListHeader(
                                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec)
                                ) {
                                    Text("Job Tracker", style = MaterialTheme.typography.titleMedium, color = Color(0xFF64B5F6))
                                }
                            }
                            item {
                                Button(
                                    onClick = {
                                        entryType = "Job"
                                        selectedWard = ""
                                        selectedAttendees = emptyList()
                                        patientName = ""
                                        patientId = ""
                                        notes = ""
                                        startTime = 0L
                                        finishTime = 0L
                                        tempSelectedStaff = emptySet()
                                        autoVoiceEnabled = false
                                        appState = "VOICE_INIT"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec)
                                ) {
                                    Text("Start New Entry", color = Color.White)
                                }
                            }
                            item {
                                Button(
                                    onClick = { appState = "HISTORY" },
                                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec)
                                ) {
                                    Text("View History")
                                }
                            }
                            item {
                                Button(
                                    onClick = { appState = "MANAGE_LISTS" },
                                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec)
                                ) {
                                    Text("Manage Lists")
                                }
                            }
                        }

                        appState == "ACTIVE" -> {
                            item {
                                Text(
                                    text = "Active $entryType",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (entryType == "Job") Color(0xFF81C784) else Color(0xFFFFB74D)
                                )
                            }
                            item { Text("Commenced: ${formatTime(startTime)}", style = MaterialTheme.typography.bodyMedium) }
                            item { Text("Loc: $selectedWard", style = MaterialTheme.typography.bodySmall) }
                            item { 
                                val attendeeText = if (selectedAttendees.isEmpty()) "None" else selectedAttendees.joinToString(", ")
                                Text("With: $attendeeText", style = MaterialTheme.typography.bodySmall) 
                            }

                            item {
                                Button(
                                    onClick = {
                                        finishTime = System.currentTimeMillis()
                                        appState = if (entryType == "Job") {
                                            "VOICE_PATIENT_NAME"
                                        } else {
                                            "VOICE_NOTES"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec)
                                ) {
                                    Text("Finish $entryType", color = Color.White)
                                }
                            }
                        }

                        appState == "SUMMARY" -> {
                            item {
                                Text("Saved to Local Storage", style = MaterialTheme.typography.titleMedium, color = Color(0xFF64B5F6))
                            }
                            item { Text("Type: $entryType", style = MaterialTheme.typography.labelSmall) }
                            item { Text("Commenced: ${formatTime(startTime)}", style = MaterialTheme.typography.labelSmall) }
                            item { Text("Concluded: ${formatTime(finishTime)}", style = MaterialTheme.typography.labelSmall) }
                            if (entryType == "Job") {
                                item { Text("Patient: $patientName", style = MaterialTheme.typography.labelSmall) }
                                item { Text("UMRN: $patientId", style = MaterialTheme.typography.labelSmall) }
                            }
                            if (notes.isNotBlank()) {
                                item { Text("Notes: $notes", style = MaterialTheme.typography.labelSmall) }
                            }
                            item {
                                Button(
                                    onClick = { appState = "IDLE" },
                                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec)
                                ) {
                                    Text("Done")
                                }
                            }
                        }

                        appState == "HISTORY" -> {
                            item { ListHeader { Text("Recent Jobs") } }
                            if (history.isEmpty()) {
                                item { Text("No history yet", style = MaterialTheme.typography.bodySmall) }
                            }
                            items(history) { record ->
                                Button(
                                    onClick = { /* Could show details */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF212121),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${record.type}: ${record.ward}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        Text(formatTime(record.end), style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                    }
                                }
                            }
                            item {
                                Button(
                                    onClick = { appState = "IDLE" },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Back") }
                            }
                            item {
                                Button(
                                    onClick = { 
                                        pendingAction = { scope.launch { ListPersistence.clearHistory(context) } }
                                        nextStateAfterPin = "HISTORY"
                                        appState = "PIN_ENTRY"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Clear History") }
                            }
                        }

                        appState == "MANAGE_LISTS" -> {
                            item { ListHeader { Text("Locations") } }
                            items(dynamicWards) { ward ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(ward, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    CompactButton(
                                        onClick = {
                                            pendingAction = {
                                                scope.launch {
                                                    ListPersistence.saveWards(context, dynamicWards.filter { it != ward })
                                                }
                                            }
                                            nextStateAfterPin = "MANAGE_LISTS"
                                            appState = "PIN_ENTRY"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                        modifier = Modifier.size(32.dp)
                                    ) { Text("-") }
                                }
                            }
                            item {
                                CompactButton(onClick = { appState = "VOICE_ADD_WARD" }) { Text("+ Add Location") }
                            }

                            item { ListHeader { Text("Attendees") } }
                            items(dynamicAttendees) { attendee ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(attendee, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    CompactButton(
                                        onClick = {
                                            pendingAction = {
                                                scope.launch {
                                                    ListPersistence.saveAttendees(context, dynamicAttendees.filter { it != attendee })
                                                }
                                            }
                                            nextStateAfterPin = "MANAGE_LISTS"
                                            appState = "PIN_ENTRY"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                        modifier = Modifier.size(32.dp)
                                    ) { Text("-") }
                                }
                            }
                            item {
                                CompactButton(onClick = { appState = "VOICE_ADD_STAFF" }) { Text("+ Add Attendee") }
                            }

                            item {
                                Button(onClick = { appState = "IDLE" }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Back")
                                }
                            }
                        }

                        appState == "PIN_ENTRY" -> {
                            item { ListHeader { Text("Enter Deletion PIN") } }
                            item {
                                Text(
                                    text = pinInput.replace(Regex("."), "*"),
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("123", "456", "789", "C0<").forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            row.forEach { char ->
                                                CompactButton(
                                                    onClick = {
                                                        when (char) {
                                                            'C' -> pinInput = ""
                                                            '<' -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                                            else -> if (pinInput.length < 4) pinInput += char
                                                        }
                                                        if (pinInput == Configuration.DELETION_PIN) {
                                                            pinInput = ""
                                                            pendingAction?.invoke()
                                                            pendingAction = null
                                                            appState = nextStateAfterPin
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) { Text(char.toString()) }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Button(onClick = { pinInput = ""; appState = nextStateAfterPin }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cancel")
                                }
                            }
                        }

                        appState == "INPUT_KEYBOARD" -> {
                            item {
                                ListHeader {
                                    Text(
                                        text = when (keyboardTarget) {
                                            "VOICE_PATIENT_NAME" -> "Patient Name"
                                            "VOICE_PATIENT_ID" -> "Patient UMRN"
                                            "VOICE_NOTES" -> "Notes"
                                            "VOICE_WARD" -> "Location"
                                            "VOICE_ADD_WARD" -> "New Location"
                                            "VOICE_ADD_STAFF" -> "New Attendee"
                                            "VOICE_STAFF" -> "New Attendee"
                                            else -> "Input"
                                        },
                                        color = Color(0xFF64B5F6)
                                    )
                                }
                            }
                            item {
                                val onConfirm = {
                                    val result = keyboardInputText.trim()
                                    when (keyboardTarget) {
                                        "VOICE_PATIENT_NAME" -> { patientName = result; appState = "VOICE_PATIENT_ID" }
                                        "VOICE_PATIENT_ID" -> { patientId = result; appState = "VOICE_NOTES" }
                                        "VOICE_NOTES" -> { notes = result; appState = "SUMMARY" }
                                        "VOICE_WARD" -> { selectedWard = result; appState = "VOICE_STAFF" }
                                        "VOICE_STAFF" -> { 
                                            if (result.isNotBlank()) {
                                                tempSelectedStaff = tempSelectedStaff + result
                                            }
                                            appState = "VOICE_STAFF" 
                                        }
                                        "VOICE_ADD_WARD" -> {
                                            if (result.isNotBlank()) {
                                                scope.launch { ListPersistence.saveWards(context, dynamicWards + result) }
                                            }
                                            appState = "MANAGE_LISTS"
                                        }
                                        "VOICE_ADD_STAFF" -> {
                                            if (result.isNotBlank()) {
                                                scope.launch { ListPersistence.saveAttendees(context, dynamicAttendees + result) }
                                            }
                                            appState = "MANAGE_LISTS"
                                        }
                                    }
                                }

                                BasicTextField(
                                    value = keyboardInputText,
                                    onValueChange = { keyboardInputText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .background(Color(0xFF212121), MaterialTheme.shapes.small)
                                        .padding(8.dp)
                                        .focusRequester(focusRequester),
                                    textStyle = TextStyle(color = Color.White),
                                    cursorBrush = SolidColor(Color.White),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                                    singleLine = true
                                )
                                LaunchedEffect(Unit) {
                                    delay(100.milliseconds)
                                    focusRequester.requestFocus()
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Button(
                                    onClick = onConfirm,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Confirm") }
                            }
                            item {
                                Button(onClick = { appState = keyboardTarget }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun launchVoiceIntent(launcher: androidx.activity.result.ActivityResultLauncher<Intent>, prompt: String) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    }
    launcher.launch(intent)
}

private fun fuzzyMatch(input: String, options: List<String>): String {
    if (input.isBlank()) return options.firstOrNull() ?: ""
    return options.find { it.contains(input, ignoreCase = true) }
        ?: options.find { input.contains(it, ignoreCase = true) }
        ?: options.firstOrNull() ?: ""
}

private fun fuzzyMatchList(input: String, options: List<String>): List<String> {
    if (input.isBlank()) return emptyList()
    val matches = options.filter { option ->
        input.contains(option, ignoreCase = true) || option.contains(input, ignoreCase = true)
    }
    return if (matches.isEmpty() && options.isNotEmpty()) listOf(options.first()) else matches
}

private fun formatTime(timeMillis: Long): String {
    if (timeMillis == 0L) return ""
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
}
