package com.example.jobtracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.speech.RecognizerIntent

class MainActivity : ComponentActivity() {
    /**
     * Taps on the tile / complication can arrive while this activity is already
     * alive, so the "quick start" request is held as observable state instead of
     * being read once from the launch intent.
     */
    private val quickStartRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quickStartRequested.value = intent.getBooleanExtra(EXTRA_QUICK_START, false)
        setContent {
            MaterialTheme {
                JobTrackerScreen(
                    quickStart = quickStartRequested.value,
                    onQuickStartConsumed = { quickStartRequested.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_QUICK_START, false)) {
            quickStartRequested.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        Syncer.syncAll(this)
    }

    companion object {
        private const val EXTRA_QUICK_START = "EXTRA_QUICK_START"
    }
}

sealed class Screen {
    data object Idle : Screen()
    data object SelectType : Screen()
    data object SelectWard : Screen()
    data object SelectAttendees : Screen()
    data object Active : Screen()
    data object Conclude : Screen()
    data object ConcludeInput : Screen()
    data object Summary : Screen()
    data object History : Screen()
    data object HistoryDetail : Screen()
    data object Settings : Screen()
    data object ReminderInterval : Screen()
    data object WatchdogThresholds : Screen()
    data object AddText : Screen()
    data object DeleteSelect : Screen()
    data object PinEntry : Screen()
}

enum class PinAction { ClearHistory, DeleteWards, DeleteAttendees, DeleteSingle }
enum class ConcludeTarget { PatientName, PatientId, Notes }

@Composable
fun JobTrackerScreen(
    quickStart: Boolean = false,
    onQuickStartConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<Screen>(Screen.Idle) }
    var activeEntry by remember { mutableStateOf(Persistence.getActiveEntry(context)) }
    var history by remember { mutableStateOf(Persistence.getHistory(context)) }
    var wards by remember { mutableStateOf(Persistence.getWards(context)) }
    var attendees by remember { mutableStateOf(Persistence.getAttendees(context)) }

    var selectedType by remember { mutableStateOf("Job") }
    var selectedWard by remember { mutableStateOf("") }
    var selectedAttendees by remember { mutableStateOf(setOf<String>()) }
    var patientName by remember { mutableStateOf("") }
    var patientId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var startTime by remember { mutableLongStateOf(0L) }
    var finishTime by remember { mutableLongStateOf(0L) }

    var selectedHistoryIndex by remember { mutableIntStateOf(-1) }
    var pinInput by remember { mutableStateOf("") }
    var pinAction by remember { mutableStateOf(PinAction.ClearHistory) }
    var pendingDelete by remember { mutableStateOf(setOf<String>()) }
    var deleteSingleIndex by remember { mutableIntStateOf(-1) }

    var addTextValue by remember { mutableStateOf("") }
    var addToTarget by remember { mutableStateOf("wards") }

    var concludeTarget by remember { mutableStateOf(ConcludeTarget.PatientName) }
    var concludeInputValue by remember { mutableStateOf("") }

    fun reloadAll() {
        activeEntry = Persistence.getActiveEntry(context)
        history = Persistence.getHistory(context)
        wards = Persistence.getWards(context)
        attendees = Persistence.getAttendees(context)
    }

    fun saveAndReturn() {
        val record = HistoryRecord(selectedType, startTime, finishTime, selectedWard, selectedAttendees.toList(), patientName, patientId, notes)
        Persistence.saveToHistory(context, record)
        Persistence.saveActiveEntry(context, null)
        context.stopService(Intent(context, JobTrackerService::class.java))
        history = Persistence.getHistory(context)
        activeEntry = null
        patientName = ""
        patientId = ""
        notes = ""
        Syncer.syncHistory(context)
        Syncer.syncActive(context)
        screen = Screen.Summary
    }

    fun startJob(type: String, ward: String, attendeeSet: Set<String>) {
        selectedType = type
        selectedWard = ward
        selectedAttendees = attendeeSet
        patientName = ""
        patientId = ""
        notes = ""
        startTime = System.currentTimeMillis()
        val entry = ActiveEntry(type, startTime, ward, attendeeSet.toList())
        Persistence.saveActiveEntry(context, entry)
        activeEntry = entry
        Syncer.syncActive(context)
        val serviceIntent = Intent(context, JobTrackerService::class.java).apply {
            putExtra("TYPE", type)
            putExtra("LOCATION", ward)
            putExtra("START_TIME", startTime)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        screen = Screen.Active
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0) return "--:--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    }

    fun formatDuration(ms: Long): String {
        val hrs = ms / 3600000
        val mins = (ms % 3600000) / 60000
        val secs = (ms % 60000) / 1000
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    val keyboardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Only apply text when the user tapped "Done" in KeyboardActivity. Cancelling
        // returns RESULT_CANCELED with no payload and must not wipe an existing
        // patient name / UMRN / notes value.
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("INPUT_TEXT")
        } else {
            null
        }
        when {
            screen == Screen.ConcludeInput && concludeTarget == ConcludeTarget.PatientName -> {
                text?.let { patientName = it }
            }
            screen == Screen.ConcludeInput && concludeTarget == ConcludeTarget.PatientId -> {
                text?.let { patientId = it }
            }
            screen == Screen.ConcludeInput && concludeTarget == ConcludeTarget.Notes -> {
                text?.let { notes = it }
            }
            screen == Screen.AddText && addToTarget == "wards" -> {
                val name = text?.trim().orEmpty()
                if (name.isNotEmpty() && name !in wards) {
                    val updated = wards + name
                    Persistence.saveWards(context, updated)
                    wards = updated
                    Syncer.syncConfig(context)
                }
            }
            screen == Screen.AddText && addToTarget == "attendees" -> {
                val name = text?.trim().orEmpty()
                if (name.isNotEmpty() && name !in attendees) {
                    val updated = attendees + name
                    Persistence.saveAttendees(context, updated)
                    attendees = updated
                    Syncer.syncConfig(context)
                }
            }
        }
        when (screen) {
            Screen.ConcludeInput -> screen = Screen.Conclude
            Screen.AddText -> screen = Screen.Settings
            else -> {}
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val extras = result.data?.extras ?: return@rememberLauncherForActivityResult
        // The recognizer returns its guesses as an ArrayList<String>; the first is
        // the best match, which is all a short note or name needs.
        val heard = extras.getCharSequenceArrayList(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.toString()?.trim().orEmpty()
        if (heard.isEmpty()) return@rememberLauncherForActivityResult
        // Names and UMRNs are single values, so they replace whatever is there (exactly
        // like the keyboard). Notes are appended so a long note can be dictated in
        // several passes without losing what was already typed.
        when (concludeTarget) {
            ConcludeTarget.PatientName -> patientName = heard
            ConcludeTarget.PatientId -> patientId = heard
            ConcludeTarget.Notes -> notes = if (notes.isBlank()) heard else "$notes $heard"
        }
        concludeInputValue = when (concludeTarget) {
            ConcludeTarget.PatientName -> patientName
            ConcludeTarget.PatientId -> patientId
            ConcludeTarget.Notes -> notes
        }
        if (screen == Screen.ConcludeInput) screen = Screen.Conclude
    }

    fun launchKeyboard(prompt: String) {
        val intent = Intent(context, KeyboardActivity::class.java).apply {
            putExtra("PROMPT", prompt)
        }
        keyboardLauncher.launch(intent)
    }

    fun launchVoice(prompt: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // No speech recognizer on this device (some watches ship without one):
            // fall back to typing rather than losing the entry point.
            launchKeyboard(prompt)
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(context as ComponentActivity, needed.toTypedArray(), 100)
        }
    }

    LaunchedEffect(quickStart) {
        if (quickStart) {
            if (activeEntry == null) {
                selectedType = "Job"
                selectedWard = ""
                selectedAttendees = emptySet()
                patientName = ""
                patientId = ""
                notes = ""
                screen = Screen.SelectType
            }
            onQuickStartConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TimeText()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 32.dp),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (screen) {
            is Screen.Idle -> {
                if (activeEntry != null) {
                    item {
                        val e = activeEntry!!
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Active ${e.type}", color = Color(0xFF64B5F6), fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(e.ward, fontSize = 12.sp)
                            if (e.attendees.isNotEmpty()) {
                                Text(e.attendees.joinToString(", "), fontSize = 10.sp, color = Color.LightGray)
                            }
                        }
                    }
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {
                                activeEntry?.let {
                                    selectedType = it.type
                                    selectedWard = it.ward
                                    selectedAttendees = it.attendees.toSet()
                                    startTime = it.startTime
                                }
                                screen = Screen.Active
                            },
                            modifier = Modifier.width(170.dp)
                        ) { Text("View Active", textAlign = TextAlign.Center) }
                    }
}
                } else {
                    item {
                        Text("No active job", color = Color.Gray, fontSize = 12.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {
                                selectedType = "Job"
                                selectedWard = ""
                                selectedAttendees = emptySet()
                                patientName = ""
                                patientId = ""
                                notes = ""
                                screen = Screen.SelectType
                            },
                            modifier = Modifier.width(170.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) { Text("Start New Job", color = Color.White, textAlign = TextAlign.Center) }
                    }
}
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { screen = Screen.History },
                        modifier = Modifier.width(170.dp)
                    ) { Text("History (${history.size})", textAlign = TextAlign.Center) }
                }
}
                if (history.isNotEmpty()) {
                    val last = history.last()
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { startJob(last.type, last.ward, last.attendees.toSet()) },
                            modifier = Modifier.width(170.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) { Text("Repeat Last Job", color = Color.White, textAlign = TextAlign.Center) }
                    }
                    }
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { screen = Screen.Settings },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Settings", textAlign = TextAlign.Center) }
                }
}
            }

            is Screen.SelectType -> {
                item { ListHeader("Select Type") }
                Config.ENTRY_TYPES.forEach { type ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {
                                selectedType = type
                                screen = Screen.SelectWard
                            },
                            modifier = Modifier.width(170.dp)
                        ) { Text(type, textAlign = TextAlign.Center) }
                    }
}
                }
                item { BackButton { screen = Screen.Idle } }
            }

            is Screen.SelectWard -> {
                item { ListHeader("Select Location") }
                wards.forEach { ward ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {
                                selectedWard = ward
                                screen = Screen.SelectAttendees
                            },
                            modifier = Modifier.width(170.dp)
                        ) { Text(ward, textAlign = TextAlign.Center) }
                    }
}
                }
                item { BackButton { screen = Screen.SelectType } }
            }

            is Screen.SelectAttendees -> {
                item { ListHeader("Select SO's") }
                // The green Start button sits at the very top so it can be reached without
                // scrolling — with no SO's selected it starts as "Start (Chat)".
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            startTime = System.currentTimeMillis()
                            val entry = ActiveEntry(selectedType, startTime, selectedWard, selectedAttendees.toList())
                            Persistence.saveActiveEntry(context, entry)
                            activeEntry = entry
                            Syncer.syncActive(context)
                            val serviceIntent = Intent(context, JobTrackerService::class.java).apply {
                                putExtra("TYPE", selectedType)
                                putExtra("LOCATION", selectedWard)
                                putExtra("START_TIME", startTime)
                            }
                            ContextCompat.startForegroundService(context, serviceIntent)
                            screen = Screen.Active
                        },
                        modifier = Modifier.width(170.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        val label = if (selectedAttendees.isEmpty()) "Start (Chat)" else "Start (${selectedAttendees.size + 1})"
                        Text(label, color = Color.White, textAlign = TextAlign.Center)
                    }
                }
}
                attendees.forEach { attendee ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val isSelected = attendee in selectedAttendees
                        Button(
                            onClick = {
                                selectedAttendees = if (isSelected) selectedAttendees - attendee else selectedAttendees + attendee
                            },
                            modifier = Modifier.width(170.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF1565C0) else Color(0xFF212121),
                                contentColor = Color.White
                            )
                        ) { Text(attendee, textAlign = TextAlign.Center) }
                    }
}
                }
                item { BackButton { screen = Screen.SelectWard } }
            }

            is Screen.Active -> {
                item {
                    var elapsed by remember { mutableLongStateOf(0L) }
                    var pulseOn by remember { mutableStateOf(true) }
                    LaunchedEffect(screen) {
                        while (screen is Screen.Active) {
                            elapsed = if (startTime > 0) System.currentTimeMillis() - startTime else 0L
                            pulseOn = !pulseOn
                            delay(1000L)
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(selectedType, color = Color(0xFF64B5F6), fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(selectedWard, fontSize = 14.sp)
                        val attendeeText = (listOf("Chat") + selectedAttendees).joinToString(", ")
                        Text(attendeeText, fontSize = 11.sp, color = Color.LightGray)
                        Spacer(Modifier.height(8.dp))
                        // Pulsing red: a live task easy to spot at a glance.
                        Text(
                            formatDuration(elapsed),
                            fontSize = 24.sp,
                            color = if (pulseOn) Color(0xFFFF5252) else Color(0xFFC62828)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${formatTime(startTime)} - now", fontSize = 10.sp, color = Color.Gray)
                    }
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            finishTime = System.currentTimeMillis()
                            screen = Screen.Conclude
                        },
                        modifier = Modifier.width(170.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) { Text("Conclude ${selectedType}", color = Color.White, textAlign = TextAlign.Center) }
                }
}
            }

            is Screen.Conclude -> {
                item { ListHeader("Conclude") }
                if (selectedType == "Job") {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {
                                concludeTarget = ConcludeTarget.PatientName
                                concludeInputValue = patientName
                                screen = Screen.ConcludeInput
                                launchKeyboard("Patient Name (optional)")
                            },
                            modifier = Modifier.width(170.dp)
                        ) {
                            Text(if (patientName.isNotBlank()) "Patient: $patientName" else "Patient Name (optional)", textAlign = TextAlign.Center)
                        }
                    }
}
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {
                                concludeTarget = ConcludeTarget.PatientId
                                concludeInputValue = patientId
                                screen = Screen.ConcludeInput
                                launchKeyboard("UMRN (optional)")
                            },
                            modifier = Modifier.width(170.dp)
                        ) {
                            Text(if (patientId.isNotBlank()) "UMRN: $patientId" else "UMRN (optional)", textAlign = TextAlign.Center)
                        }
                    }
}
                }
                // Notes can be typed or dictated; both end up on the same field.
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            concludeTarget = ConcludeTarget.Notes
                            concludeInputValue = notes
                            screen = Screen.ConcludeInput
                            launchKeyboard("Notes (optional)")
                        },
                        modifier = Modifier.width(170.dp)
                    ) {
                        Text(
                            if (notes.isNotBlank()) "Type Notes: ${notes.take(14)}" else "Type Notes",
                            textAlign = TextAlign.Center
                        )
                    }
                }
}
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            concludeTarget = ConcludeTarget.Notes
                            concludeInputValue = notes
                            launchVoice("Speak notes")
                        },
                        modifier = Modifier.width(170.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD), contentColor = Color.White)
                    ) { Text("Speak Notes", textAlign = TextAlign.Center) }
                }
}
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { saveAndReturn() },
                        modifier = Modifier.width(170.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Text("Save ${selectedType}", color = Color.White, textAlign = TextAlign.Center) }
                }
}
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { saveAndReturn() },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Skip All & Save", textAlign = TextAlign.Center) }
                }
}
            }

            is Screen.Summary -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Saved", color = Color(0xFF64B5F6), fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${selectedType} | ${formatTime(startTime)} - ${formatTime(finishTime)}", fontSize = 11.sp)
                        Text(selectedWard, fontSize = 12.sp)
                        if (patientName.isNotBlank()) Text("Patient: $patientName", fontSize = 10.sp, color = Color.LightGray)
                        if (patientId.isNotBlank()) Text("UMRN: $patientId", fontSize = 10.sp, color = Color.LightGray)
                    }
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { screen = Screen.Idle },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Done", textAlign = TextAlign.Center) }
                }
}
            }

            is Screen.History -> {
                item { ListHeader("Recent Jobs") }
                if (history.isEmpty()) {
                    item { Text("No history", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = Color.Gray) }
                }
                history.asReversed().forEachIndexed { reversedIdx, record ->
                    val realIdx = history.size - 1 - reversedIdx
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { selectedHistoryIndex = realIdx; screen = Screen.HistoryDetail },
                            modifier = Modifier.width(170.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121))
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${record.type}: ${record.ward}", fontSize = 11.sp, color = Color.White)
                                Text(formatTime(record.endTime), fontSize = 10.sp, color = Color.LightGray)
                            }
                        }
                    }
}
                }
                item { BackButton { screen = Screen.Idle } }
            }

            is Screen.HistoryDetail -> {
                if (selectedHistoryIndex in history.indices) {
                    val record = history[selectedHistoryIndex]
                    item { ListHeader("${record.type} Details") }
                    item { DetailRow("Location", record.ward) }
                    item { DetailRow("Time", "${formatTime(record.startTime)} - ${formatTime(record.endTime)}") }
                    val dur = record.endTime - record.startTime
                    if (dur > 0) item { DetailRow("Duration", formatDuration(dur)) }
                    val attText = (listOf("Chat") + record.attendees).joinToString(", ")
                    item { DetailRow("SO's", attText) }
                    if (record.patientName.isNotBlank()) item { DetailRow("Patient", record.patientName) }
                    if (record.patientId.isNotBlank()) item { DetailRow("UMRN", record.patientId) }
                    if (record.notes.isNotBlank()) item { DetailRow("Notes", record.notes) }
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            deleteSingleIndex = selectedHistoryIndex
                            pinAction = PinAction.DeleteSingle
                            pinInput = ""
                            screen = Screen.PinEntry
                        },
                        modifier = Modifier.width(170.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121), contentColor = Color.White)
                    ) { Text("Delete This Entry", textAlign = TextAlign.Center) }
                }
                }
                item { BackButton { screen = Screen.History } }
            }

            is Screen.Settings -> {
                item { ListHeader("Settings") }

                // Reminder interval picker
                val reminderMin = Persistence.getReminderMinutes(context)
                item {
                    Text("Reminder interval: ${if (reminderMin == 0) "off" else "$reminderMin min"}", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
                item {
                    Button(
                        onClick = { screen = Screen.ReminderInterval },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Change…", textAlign = TextAlign.Center) }
                }

                // Long-job watchdog thresholds
                item {
                    Text(
                        "Watchdog: warn ${Persistence.getWarnHours(context)}h, critical ${Persistence.getCritHours(context)}h",
                        fontSize = 12.sp, color = Color.LightGray,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { screen = Screen.WatchdogThresholds },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Watchdog Settings", textAlign = TextAlign.Center) }
                }
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { addToTarget = "wards"; addTextValue = ""; screen = Screen.AddText; launchKeyboard("New Location") },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Add Location", textAlign = TextAlign.Center) }
                }
}
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { addToTarget = "attendees"; addTextValue = ""; screen = Screen.AddText; launchKeyboard("New SO") },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Add SO", textAlign = TextAlign.Center) }
                }
}
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            pendingDelete = emptySet()
                            pinAction = PinAction.DeleteWards
                            pinInput = ""
                            screen = Screen.PinEntry
                        },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Delete Locations", textAlign = TextAlign.Center) }
                }
}
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            pendingDelete = emptySet()
                            pinAction = PinAction.DeleteAttendees
                            pinInput = ""
                            screen = Screen.PinEntry
                        },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Delete SO's", textAlign = TextAlign.Center) }
                }
}
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            pendingDelete = emptySet()
                            pinAction = PinAction.ClearHistory
                            pinInput = ""
                            screen = Screen.PinEntry
                        },
                        modifier = Modifier.width(170.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) { Text("Clear History", color = Color.White, textAlign = TextAlign.Center) }
                }
}
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { scope.launch(Dispatchers.IO) { Syncer.syncAll(context) } },
                        modifier = Modifier.width(170.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) { Text("Sync Now", color = Color.White, textAlign = TextAlign.Center) }
                }
}
                item { BackButton { screen = Screen.Idle } }
            }

            is Screen.ReminderInterval -> {
                item { ListHeader("Buzz Every") }
                val current = Persistence.getReminderMinutes(context)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf(0 to "Off", 1 to "1m", 2 to "2m").forEach { (mins, label) ->
                            Button(
                                onClick = { Persistence.setReminderMinutes(context, mins); screen = Screen.Settings },
                                modifier = Modifier.width(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (current == mins) Color(0xFF2E7D32) else Color(0xFF212121),
                                    contentColor = Color.White
                                )
                            ) { Text(label, fontSize = 11.sp, textAlign = TextAlign.Center) }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf(5 to "5m", 10 to "10m", 30 to "30m").forEach { (mins, label) ->
                            Button(
                                onClick = { Persistence.setReminderMinutes(context, mins); screen = Screen.Settings },
                                modifier = Modifier.width(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (current == mins) Color(0xFF2E7D32) else Color(0xFF212121),
                                    contentColor = Color.White
                                )
                            ) { Text(label, fontSize = 11.sp, textAlign = TextAlign.Center) }
                        }
                    }
                }
                item {
                    Text(
                        if (current == 0) "Nudges off" else "Nudges every $current min",
                        fontSize = 11.sp, color = Color.LightGray, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { BackButton { screen = Screen.Settings } }
            }

            is Screen.WatchdogThresholds -> {
                item { ListHeader("Warn After") }
                val warn = Persistence.getWarnHours(context)
                val crit = Persistence.getCritHours(context)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf(1, 2, 4).forEach { hours ->
                            Button(
                                onClick = {
                                    Persistence.setWarnHours(context, hours)
                                    // Critical must stay above the warning threshold.
                                    if (Persistence.getCritHours(context) <= hours) {
                                        Persistence.setCritHours(context, hours + 1)
                                    }
                                    screen = Screen.Settings
                                },
                                modifier = Modifier.width(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (warn == hours) Color(0xFF2E7D32) else Color(0xFF212121),
                                    contentColor = Color.White
                                )
                            ) { Text("${hours}h", fontSize = 11.sp, textAlign = TextAlign.Center) }
                        }
                    }
                }
                item { ListHeader("Critical After") }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf(warn + 1, warn + 2, warn + 4).forEach { hours ->
                            Button(
                                onClick = { Persistence.setCritHours(context, hours); screen = Screen.Settings },
                                modifier = Modifier.width(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (crit == hours) Color(0xFF2E7D32) else Color(0xFF212121),
                                    contentColor = Color.White
                                )
                            ) { Text("${hours}h", fontSize = 11.sp, textAlign = TextAlign.Center) }
                        }
                    }
                }
                item { BackButton { screen = Screen.Settings } }
            }

            is Screen.DeleteSelect -> {
                val items = when (pinAction) {
                    PinAction.DeleteWards -> wards
                    PinAction.DeleteAttendees -> attendees
                    else -> emptyList()
                }
                val label = when (pinAction) {
                    PinAction.DeleteWards -> "Locations"
                    PinAction.DeleteAttendees -> "SO's"
                    else -> ""
                }
                item { ListHeader("Select $label to Delete") }
                items.forEach { item ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val isSelected = item in pendingDelete
                        Button(
                            onClick = {
                                pendingDelete = if (isSelected) pendingDelete - item else pendingDelete + item
                            },
                            modifier = Modifier.width(170.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFFC62828) else Color(0xFF212121),
                                contentColor = Color.White
                            )
                        ) { Text(item, textAlign = TextAlign.Center) }
                    }
}
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            when (pinAction) {
                                PinAction.DeleteWards -> { Persistence.deleteWards(context, pendingDelete); wards = Persistence.getWards(context) }
                                PinAction.DeleteAttendees -> { Persistence.deleteAttendees(context, pendingDelete); attendees = Persistence.getAttendees(context) }
                                else -> {}
                            }
                            Syncer.syncConfig(context)
                            screen = Screen.Settings
                        },
                        modifier = Modifier.width(170.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        enabled = pendingDelete.isNotEmpty()
                    ) { Text("Delete (${pendingDelete.size})", color = Color.White, textAlign = TextAlign.Center) }
                }
}
                item { BackButton { screen = Screen.Settings } }
            }

            is Screen.PinEntry -> {
                item { ListHeader("Enter PIN") }
                item {
                    Text(
                        text = pinInput.map { "*" }.joinToString("").ifBlank { "_" }.padEnd(4, '_'),
                        fontSize = 24.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = Color(0xFF64B5F6)
                    )
                }
                val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("C","0","<"))
                rows.forEach { row ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            row.forEach { char ->
                                CompactButton(
                                    onClick = {
                                        when (char) {
                                            "C" -> pinInput = ""
                                            "<" -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                            else -> if (pinInput.length < 4) pinInput += char
                                        }
                                        if (Config.isDeletionPin(pinInput)) {
                                            pinInput = ""
                                            when (pinAction) {
                                                PinAction.ClearHistory -> {
                                                    Persistence.clearHistory(context)
                                                    history = emptyList()
                                                    Syncer.syncHistory(context)
                                                    screen = Screen.Settings
                                                }
                                                PinAction.DeleteWards, PinAction.DeleteAttendees -> {
                                                    screen = Screen.DeleteSelect
                                                }
                                                PinAction.DeleteSingle -> {
                                                    Persistence.deleteHistoryAt(context, deleteSingleIndex)
                                                    history = Persistence.getHistory(context)
                                                    deleteSingleIndex = -1
                                                    screen = Screen.History
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) { Text(char, textAlign = TextAlign.Center) }
                            }
                        }
                    }
                }
                item { BackButton { screen = Screen.Settings } }
            }

            is Screen.AddText -> {
                item { ListHeader("Enter Name") }
                item {
                    Text(addTextValue.ifBlank { "_" }, fontSize = 14.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { launchKeyboard("Enter name") },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Type", textAlign = TextAlign.Center) }
                }
}
                item { BackButton { screen = Screen.Settings } }
            }

            is Screen.ConcludeInput -> {
                item { ListHeader(if (concludeTarget == ConcludeTarget.Notes) "Notes" else "Enter Text") }
                item {
                    Text(concludeInputValue.ifBlank { "_" }, fontSize = 14.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { launchKeyboard("Enter text") },
                        modifier = Modifier.width(170.dp)
                    ) { Text("Type", textAlign = TextAlign.Center) }
                }
}
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { launchVoice("Speak") },
                        modifier = Modifier.width(170.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD), contentColor = Color.White)
                    ) { Text("Speak", textAlign = TextAlign.Center) }
                }
}
                item { BackButton { screen = Screen.Conclude } }
            }
        }
        }
    }
}

@Composable
fun ListHeader(text: String) {
    Text(text, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        color = Color(0xFF64B5F6))
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp)
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFC62828),
                contentColor = Color.White
            )
        ) { Text("Back", fontSize = 13.sp, textAlign = TextAlign.Center) }
    }
}
