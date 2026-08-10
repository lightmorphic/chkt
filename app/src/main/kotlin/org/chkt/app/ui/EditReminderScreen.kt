package org.chkt.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.chkt.app.data.AlertMode
import org.chkt.app.data.LocationTrigger
import org.chkt.app.data.Reminder
import org.chkt.app.domain.RepeatRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderScreen(
    listId: String,
    reminderId: String?,
    onDone: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var loaded by remember { mutableStateOf(reminderId == null) }
    var original by remember { mutableStateOf<Reminder?>(null) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.of((LocalTime.now().hour + 1) % 24, 0)) }
    var rule by remember { mutableStateOf<RepeatRule>(RepeatRule.None) }
    var alertMode by remember { mutableStateOf(AlertMode.RING_AND_SPEAK) }
    var preTone by remember { mutableStateOf(false) }
    var vibrate by remember { mutableStateOf(true) }
    var respectDnd by remember { mutableStateOf(false) }
    var nagInterval by remember { mutableStateOf(0) }
    var nagStopAfter by remember { mutableStateOf(60) }
    var deleteAfterDismissed by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    var chosenListId by remember { mutableStateOf(listId) }
    val allLists by repo.db.lists().observeAll().collectAsState(initial = emptyList())
    var locationTrigger by remember { mutableStateOf(LocationTrigger.NONE) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var radius by remember { mutableStateOf(150f) }
    var locationNote by remember { mutableStateOf("") }

    LaunchedEffect(reminderId) {
        if (reminderId != null) {
            val r = withContext(Dispatchers.IO) { repo.db.reminders().byId(reminderId) }
            if (r != null) {
                original = r
                title = r.title; notes = r.notes
                r.dueAt?.let {
                    val zdt = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                    date = zdt.toLocalDate(); time = zdt.toLocalTime().withSecond(0).withNano(0)
                } ?: run { date = null }
                rule = RepeatRule.decode(r.repeatRule)
                alertMode = r.alertMode; preTone = r.preTone
                vibrate = r.vibrate; respectDnd = r.respectDnd
                nagInterval = r.nagIntervalMinutes; nagStopAfter = r.nagStopAfterMinutes
                deleteAfterDismissed = r.deleteAfterDismissed
                active = r.enabled
                chosenListId = r.listId
                locationTrigger = r.locationTrigger
                latitude = r.latitude; longitude = r.longitude; radius = r.radiusMetres
            }
            loaded = true
        }
    }

    fun save() {
        val dueAt = date?.let {
            it.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val base = original
        val reminder = (base ?: Reminder(listId = chosenListId, title = "", dueAt = null)).copy(
            listId = chosenListId,
            title = title.trim(),
            notes = notes.trim(),
            dueAt = dueAt,
            repeatRule = rule.encode(),
            alertMode = alertMode,
            preTone = preTone,
            vibrate = vibrate,
            respectDnd = respectDnd,
            nagIntervalMinutes = nagInterval,
            nagStopAfterMinutes = nagStopAfter,
            nagStartedAt = null,
            deleteAfterDismissed = deleteAfterDismissed,
            enabled = active,
            snoozedUntil = null,
            locationTrigger = locationTrigger,
            latitude = latitude,
            longitude = longitude,
            radiusMetres = radius,
        )
        scope.launch {
            repo.saveReminder(reminder)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reminderId == null) "New reminder" else "Edit reminder") },
                navigationIcon = { IconButton24(ChktIcon.Back, "Back", MaterialTheme.colorScheme.onSurface, onClick = onDone) },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("What to remind you about") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (also spoken)") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (allLists.size > 1) {
                SectionLabel("List")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allLists.forEach { l ->
                        FilterChip(
                            selected = chosenListId == l.id,
                            onClick = { chosenListId = l.id },
                            label = { Text(l.name) },
                        )
                    }
                }
            }

            SectionLabel("When")
            DateTimeRow(
                date = date, time = time,
                onDate = { date = it }, onTime = { time = it },
                onClearDate = { date = null },
            )

            SectionLabel("Repeat")
            RepeatPicker(rule = rule, time = time, onChange = { rule = it })

            SectionLabel("Alert")
            AlertModePicker(alertMode) { alertMode = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = preTone, onCheckedChange = { preTone = it })
                Text("  Play a tone before speaking", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                Text("  Vibrate", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = respectDnd, onCheckedChange = { respectDnd = it })
                Text("  Stay quiet during Do Not Disturb", style = MaterialTheme.typography.bodyMedium)
            }

            SectionLabel("If not answered")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = nagInterval == 0, onClick = { nagInterval = 0 }, label = { Text("Alert once") })
                FilterChip(selected = nagInterval == 1, onClick = { nagInterval = 1 }, label = { Text("1 min") })
                FilterChip(selected = nagInterval == 2, onClick = { nagInterval = 2 }, label = { Text("2 min") })
                FilterChip(selected = nagInterval == 5, onClick = { nagInterval = 5 }, label = { Text("5 min") })
            }
            if (nagInterval > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { minutes ->
                        FilterChip(
                            selected = nagStopAfter == minutes,
                            onClick = { nagStopAfter = minutes },
                            label = { Text(if (minutes < 60) "stop after $minutes min" else "stop after ${minutes / 60} hr") },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = deleteAfterDismissed, onCheckedChange = { deleteAfterDismissed = it })
                Text("  Delete once dismissed", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = active, onCheckedChange = { active = it })
                Text("  Active", style = MaterialTheme.typography.bodyMedium)
            }

            SectionLabel("Location")
            LocationPicker(
                trigger = locationTrigger,
                latitude = latitude, longitude = longitude, radius = radius,
                note = locationNote,
                onTrigger = { locationTrigger = it },
                onUseCurrent = {
                    val loc = lastKnownLocation(context)
                    if (loc != null) {
                        latitude = loc.first; longitude = loc.second
                        locationNote = "Place set to where you are now."
                    } else {
                        locationNote = "Couldn't read your location, check location is on and permission granted."
                    }
                },
                onRadius = { radius = it },
            )

            Button(
                onClick = ::save,
                enabled = title.isNotBlank() && (date != null || locationTrigger != LocationTrigger.NONE),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save reminder") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    date: LocalDate?,
    time: LocalTime,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
    onClearDate: () -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { showDate = true }) {
            Text(date?.format(dateFormat) ?: "No date (location only)")
        }
        OutlinedButton(onClick = { showTime = true }) { Text(time.format(timeFormat)) }
        if (date != null) {
            TextButton(onClick = onClearDate) { Text("Clear") }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now())
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDate(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    showDate = false
                }) { Text("OK") }
            },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        Dialog(onDismissRequest = { showTime = false }) {
            androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = state)
                    TextButton(onClick = {
                        onTime(LocalTime.of(state.hour, state.minute)); showTime = false
                    }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun RepeatPicker(rule: RepeatRule, time: LocalTime, onChange: (RepeatRule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = rule is RepeatRule.None, onClick = { onChange(RepeatRule.None) }, label = { Text("Once") })
            FilterChip(selected = rule is RepeatRule.Daily, onClick = { onChange(RepeatRule.Daily) }, label = { Text("Daily") })
            FilterChip(
                selected = rule is RepeatRule.Weekly,
                onClick = { onChange(RepeatRule.Weekly(setOf(LocalDate.now().dayOfWeek))) },
                label = { Text("Weekly") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rule is RepeatRule.Monthly,
                onClick = { onChange(RepeatRule.Monthly(LocalDate.now().dayOfMonth)) },
                label = { Text("Monthly") },
            )
            FilterChip(
                selected = rule is RepeatRule.Yearly,
                onClick = { onChange(RepeatRule.Yearly(MonthDay.from(LocalDate.now()))) },
                label = { Text("Yearly") },
            )
            FilterChip(
                selected = rule is RepeatRule.Every,
                onClick = { onChange(RepeatRule.Every(java.time.Duration.ofDays(2))) },
                label = { Text("Custom") },
            )
        }

        when (rule) {
            is RepeatRule.Weekly -> {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in rule.days,
                            onClick = {
                                val days = if (day in rule.days) rule.days - day else rule.days + day
                                onChange(RepeatRule.Weekly(days.ifEmpty { setOf(day) }))
                            },
                            label = { Text(day.name.take(2).lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
            }
            is RepeatRule.Monthly -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (rule.last) "" else rule.dayOfMonth.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { if (it in 1..31) onChange(RepeatRule.Monthly(it)) }
                        },
                        label = { Text("Day of month") },
                        enabled = !rule.last,
                        modifier = Modifier.fillMaxWidth(0.4f),
                    )
                    FilterChip(
                        selected = rule.last,
                        onClick = { onChange(RepeatRule.Monthly(31, last = !rule.last)) },
                        label = { Text("Last day") },
                    )
                }
            }
            is RepeatRule.Every -> {
                var amount by remember { mutableStateOf("2") }
                var unit by remember { mutableStateOf("d") }
                fun push() {
                    val n = amount.toLongOrNull() ?: return
                    if (n <= 0) return
                    val d = when (unit) {
                        "m" -> java.time.Duration.ofMinutes(n)
                        "h" -> java.time.Duration.ofHours(n)
                        "d" -> java.time.Duration.ofDays(n)
                        else -> java.time.Duration.ofDays(7 * n)
                    }
                    onChange(RepeatRule.Every(d))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it; push() },
                        label = { Text("Every") },
                        modifier = Modifier.fillMaxWidth(0.3f),
                    )
                    listOf("m" to "min", "h" to "hrs", "d" to "days", "w" to "wks").forEach { (code, label) ->
                        FilterChip(selected = unit == code, onClick = { unit = code; push() }, label = { Text(label) })
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun AlertModePicker(mode: AlertMode, onChange: (AlertMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == AlertMode.RING_AND_SPEAK, onClick = { onChange(AlertMode.RING_AND_SPEAK) }, label = { Text("Ring + speak") })
            FilterChip(selected = mode == AlertMode.RING_ONLY, onClick = { onChange(AlertMode.RING_ONLY) }, label = { Text("Ring only") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == AlertMode.SPEAK_ONLY, onClick = { onChange(AlertMode.SPEAK_ONLY) }, label = { Text("Speak only") })
            FilterChip(selected = mode == AlertMode.NOTIFY_ONLY, onClick = { onChange(AlertMode.NOTIFY_ONLY) }, label = { Text("Notification only") })
        }
    }
}

@Composable
private fun LocationPicker(
    trigger: LocationTrigger,
    latitude: Double?,
    longitude: Double?,
    radius: Float,
    note: String,
    onTrigger: (LocationTrigger) -> Unit,
    onUseCurrent: () -> Unit,
    onRadius: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = trigger == LocationTrigger.NONE, onClick = { onTrigger(LocationTrigger.NONE) }, label = { Text("Off") })
            FilterChip(selected = trigger == LocationTrigger.ARRIVE, onClick = { onTrigger(LocationTrigger.ARRIVE) }, label = { Text("When I arrive") })
            FilterChip(selected = trigger == LocationTrigger.LEAVE, onClick = { onTrigger(LocationTrigger.LEAVE) }, label = { Text("When I leave") })
        }
        if (trigger != LocationTrigger.NONE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUseCurrent) { Text("Use where I am now") }
                Text(
                    if (latitude != null && longitude != null) "Place set ✓ (${radius.toInt()} m)" else "No place set",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100f, 150f, 300f, 500f).forEach { r ->
                    FilterChip(selected = radius == r, onClick = { onRadius(r) }, label = { Text("${r.toInt()} m") })
                }
            }
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@SuppressLint("MissingPermission")
internal fun lastKnownLocation(context: Context): Pair<Double, Double>? = try {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val loc = lm.allProviders.asSequence()
        .mapNotNull { lm.getLastKnownLocation(it) }
        .maxByOrNull { it.time }
    loc?.let { it.latitude to it.longitude }
} catch (e: SecurityException) {
    null
}
