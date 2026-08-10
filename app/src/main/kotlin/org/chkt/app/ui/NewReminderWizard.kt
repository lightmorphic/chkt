package org.chkt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

/**
 * Creating a reminder is a conversation, not a form: the app asks one
 * question at a time, the way Prodder does. What? When? What time?
 * Repeat? How should it alert you? Then a summary you confirm.
 */
private enum class Step { WHAT, WHEN, TIME, REPEAT, ALERT, CONFIRM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewReminderWizard(
    listId: String,
    onDone: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var step by rememberSaveable { mutableStateOf(Step.WHAT) }
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var rule by remember { mutableStateOf<RepeatRule>(RepeatRule.None) }
    var alertMode by rememberSaveable { mutableStateOf(AlertMode.RING_AND_SPEAK) }
    var preTone by rememberSaveable { mutableStateOf(false) }
    var locationTrigger by rememberSaveable { mutableStateOf(LocationTrigger.NONE) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var radius by remember { mutableStateOf(150f) }

    val atAPlace = locationTrigger != LocationTrigger.NONE

    fun back() {
        step = when (step) {
            Step.WHAT -> { onDone(); return }
            Step.WHEN -> Step.WHAT
            Step.TIME -> Step.WHEN
            Step.REPEAT -> if (atAPlace) Step.WHEN else Step.TIME
            Step.ALERT -> if (atAPlace) Step.WHEN else Step.REPEAT
            Step.CONFIRM -> Step.ALERT
        }
    }

    fun save() {
        val dueAt = if (!atAPlace && date != null && time != null) {
            date!!.atTime(time!!).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else null
        scope.launch {
            repo.saveReminder(
                Reminder(
                    listId = listId,
                    title = title.trim(),
                    notes = notes.trim(),
                    dueAt = dueAt,
                    repeatRule = if (atAPlace) "" else rule.encode(),
                    alertMode = alertMode,
                    preTone = preTone,
                    locationTrigger = locationTrigger,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMetres = radius,
                )
            )
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New reminder") },
                navigationIcon = {
                    IconButton24(ChktIcon.Back, "Back", MaterialTheme.colorScheme.onSurface, onClick = ::back)
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LinearProgressIndicator(
                progress = { (step.ordinal + 1) / Step.entries.size.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )

            when (step) {
                Step.WHAT -> {
                    Question("What should I remind you about?")
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("The reminder") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        label = { Text("Anything extra to say out loud? (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NextButton(enabled = title.isNotBlank()) { step = Step.WHEN }
                }

                Step.WHEN -> {
                    Question("When should I remind you?")
                    WhenPicker(
                        date = date,
                        locationTrigger = locationTrigger,
                        onDate = { date = it; locationTrigger = LocationTrigger.NONE },
                        onLocationTrigger = { locationTrigger = it; date = null },
                    )
                    if (atAPlace) {
                        PlacePicker(
                            trigger = locationTrigger,
                            latitude = latitude, longitude = longitude, radius = radius,
                            onUseCurrent = {
                                lastKnownLocation(context)?.let { (la, lo) ->
                                    latitude = la; longitude = lo
                                }
                            },
                            onRadius = { radius = it },
                        )
                        NextButton(enabled = latitude != null && longitude != null) { step = Step.ALERT }
                    } else {
                        NextButton(enabled = date != null) { step = Step.TIME }
                    }
                }

                Step.TIME -> {
                    Question("What time?")
                    val state = rememberTimePickerState(
                        initialHour = time?.hour ?: (LocalTime.now().hour + 1) % 24,
                        initialMinute = time?.minute ?: 0,
                        is24Hour = true,
                    )
                    TimePicker(state = state)
                    NextButton(enabled = true) {
                        time = LocalTime.of(state.hour, state.minute)
                        step = Step.REPEAT
                    }
                }

                Step.REPEAT -> {
                    Question("Should it repeat?")
                    RepeatChips(rule = rule, onChange = { rule = it })
                    NextButton(enabled = true) { step = Step.ALERT }
                }

                Step.ALERT -> {
                    Question("How should I get your attention?")
                    AlertChips(alertMode) { alertMode = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = preTone, onCheckedChange = { preTone = it })
                        Text("  Play a tone before speaking", style = MaterialTheme.typography.bodyMedium)
                    }
                    NextButton(enabled = true) { step = Step.CONFIRM }
                }

                Step.CONFIRM -> {
                    Question("All set?")
                    Text(summaryText(title, notes, date, time, rule, alertMode, preTone, locationTrigger),
                        style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = ::save,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Save reminder", fontSize = 18.sp) }
                    TextButton(onClick = { step = Step.WHAT }) { Text("Start again") }
                }
            }
        }
    }
}

@Composable
private fun Question(text: String) {
    Text(text, fontSize = 26.sp, lineHeight = 34.sp, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun NextButton(enabled: Boolean, onClick: () -> Unit) {
    Spacer(Modifier.height(4.dp))
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text("Next", fontSize = 18.sp) }
}

private val wizardDateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenPicker(
    date: LocalDate?,
    locationTrigger: LocationTrigger,
    onDate: (LocalDate) -> Unit,
    onLocationTrigger: (LocationTrigger) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = date == LocalDate.now(),
                onClick = { onDate(LocalDate.now()) },
                label = { Text("Today") },
            )
            FilterChip(
                selected = date == LocalDate.now().plusDays(1),
                onClick = { onDate(LocalDate.now().plusDays(1)) },
                label = { Text("Tomorrow") },
            )
            FilterChip(
                selected = date != null && date != LocalDate.now() && date != LocalDate.now().plusDays(1),
                onClick = { showPicker = true },
                label = { Text(if (date != null && date != LocalDate.now() && date != LocalDate.now().plusDays(1)) date.format(wizardDateFormat) else "Pick a date") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = locationTrigger == LocationTrigger.ARRIVE,
                onClick = { onLocationTrigger(LocationTrigger.ARRIVE) },
                label = { Text("When I arrive somewhere") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = locationTrigger == LocationTrigger.LEAVE,
                onClick = { onLocationTrigger(LocationTrigger.LEAVE) },
                label = { Text("When I leave somewhere") },
            )
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now())
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDate(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun PlacePicker(
    trigger: LocationTrigger,
    latitude: Double?,
    longitude: Double?,
    radius: Float,
    onUseCurrent: () -> Unit,
    onRadius: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (trigger == LocationTrigger.ARRIVE) "Where will you be arriving?" else "Where will you be leaving?",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUseCurrent) { Text("Use where I am now") }
            Text(
                if (latitude != null && longitude != null) "Place set" else "No place set yet",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100f, 150f, 300f, 500f).forEach { r ->
                FilterChip(selected = radius == r, onClick = { onRadius(r) }, label = { Text("${r.toInt()} m") })
            }
        }
    }
}

@Composable
private fun RepeatChips(rule: RepeatRule, onChange: (RepeatRule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = rule is RepeatRule.None, onClick = { onChange(RepeatRule.None) }, label = { Text("Just once") })
            FilterChip(selected = rule is RepeatRule.Daily, onClick = { onChange(RepeatRule.Daily) }, label = { Text("Every day") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rule is RepeatRule.Weekly,
                onClick = { onChange(RepeatRule.Weekly(setOf(LocalDate.now().dayOfWeek))) },
                label = { Text("Weekly") },
            )
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
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rule is RepeatRule.Every,
                onClick = { onChange(RepeatRule.Every(java.time.Duration.ofDays(2))) },
                label = { Text("Custom interval") },
            )
        }
        if (rule is RepeatRule.Weekly) {
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
        if (rule is RepeatRule.Every) {
            var amount by remember { mutableStateOf("2") }
            var unit by remember { mutableStateOf("d") }
            fun push() {
                val n = amount.toLongOrNull() ?: return
                if (n <= 0) return
                onChange(RepeatRule.Every(when (unit) {
                    "m" -> java.time.Duration.ofMinutes(n)
                    "h" -> java.time.Duration.ofHours(n)
                    "d" -> java.time.Duration.ofDays(n)
                    else -> java.time.Duration.ofDays(7 * n)
                }))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it; push() },
                    label = { Text("Every") }, modifier = Modifier.fillMaxWidth(0.3f),
                )
                listOf("m" to "min", "h" to "hrs", "d" to "days", "w" to "wks").forEach { (code, label) ->
                    FilterChip(selected = unit == code, onClick = { unit = code; push() }, label = { Text(label) })
                }
            }
        }
    }
}

@Composable
private fun AlertChips(mode: AlertMode, onChange: (AlertMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == AlertMode.RING_AND_SPEAK, onClick = { onChange(AlertMode.RING_AND_SPEAK) }, label = { Text("Ring, then speak") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == AlertMode.SPEAK_ONLY, onClick = { onChange(AlertMode.SPEAK_ONLY) }, label = { Text("Just speak") })
            FilterChip(selected = mode == AlertMode.RING_ONLY, onClick = { onChange(AlertMode.RING_ONLY) }, label = { Text("Just ring") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == AlertMode.NOTIFY_ONLY, onClick = { onChange(AlertMode.NOTIFY_ONLY) }, label = { Text("Silent notification") })
        }
    }
}

private fun summaryText(
    title: String,
    notes: String,
    date: LocalDate?,
    time: LocalTime?,
    rule: RepeatRule,
    mode: AlertMode,
    preTone: Boolean,
    trigger: LocationTrigger,
): String {
    val whenPart = when {
        trigger == LocationTrigger.ARRIVE -> "when you arrive"
        trigger == LocationTrigger.LEAVE -> "when you leave"
        date != null && time != null -> "${date.format(wizardDateFormat)} at %02d:%02d".format(time.hour, time.minute)
        else -> ""
    }
    val repeatPart = when (rule) {
        RepeatRule.None -> ""
        RepeatRule.Daily -> ", repeating daily"
        is RepeatRule.Weekly -> ", repeating weekly"
        is RepeatRule.Monthly -> ", repeating monthly"
        is RepeatRule.Yearly -> ", repeating yearly"
        is RepeatRule.Every -> ", repeating on a custom interval"
    }
    val alertPart = when (mode) {
        AlertMode.RING_AND_SPEAK -> "I'll ring, then say it out loud"
        AlertMode.SPEAK_ONLY -> "I'll say it out loud"
        AlertMode.RING_ONLY -> "I'll ring"
        AlertMode.NOTIFY_ONLY -> "You'll get a silent notification"
    } + if (preTone && mode != AlertMode.RING_ONLY && mode != AlertMode.NOTIFY_ONLY) ", with a tone first." else "."
    val notesPart = if (notes.isNotBlank()) "\n\nExtra words: $notes" else ""
    return "“$title”\n$whenPart$repeatPart.\n\n$alertPart$notesPart"
}
