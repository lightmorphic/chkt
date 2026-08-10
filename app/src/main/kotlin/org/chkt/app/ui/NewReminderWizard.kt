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
import androidx.compose.runtime.collectAsState
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
private enum class Step { WHAT, WHEN, TIME, REPEAT, ALERT, NAG, EXTRAS, CONFIRM }

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
    var vibrate by rememberSaveable { mutableStateOf(true) }
    var respectDnd by rememberSaveable { mutableStateOf(false) }
    var nagInterval by rememberSaveable { mutableStateOf(0) }
    var nagStopAfter by rememberSaveable { mutableStateOf(60) }
    var deleteAfterDismissed by rememberSaveable { mutableStateOf(false) }
    var active by rememberSaveable { mutableStateOf(true) }
    var chosenListId by rememberSaveable { mutableStateOf(listId) }
    var locationTrigger by rememberSaveable { mutableStateOf(LocationTrigger.NONE) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var radius by remember { mutableStateOf(150f) }

    val lists by repo.db.lists().observeAll().collectAsState(initial = emptyList())

    val atAPlace = locationTrigger != LocationTrigger.NONE

    fun back() {
        step = when (step) {
            Step.WHAT -> { onDone(); return }
            Step.WHEN -> Step.WHAT
            Step.TIME -> Step.WHEN
            Step.REPEAT -> if (atAPlace) Step.WHEN else Step.TIME
            Step.ALERT -> if (atAPlace) Step.WHEN else Step.REPEAT
            Step.NAG -> Step.ALERT
            Step.EXTRAS -> Step.NAG
            Step.CONFIRM -> Step.EXTRAS
        }
    }

    fun save() {
        val dueAt = if (!atAPlace && date != null && time != null) {
            date!!.atTime(time!!).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else null
        scope.launch {
            repo.saveReminder(
                Reminder(
                    listId = chosenListId,
                    title = title.trim(),
                    notes = notes.trim(),
                    dueAt = dueAt,
                    repeatRule = if (atAPlace) "" else rule.encode(),
                    alertMode = alertMode,
                    preTone = preTone,
                    enabled = active,
                    vibrate = vibrate,
                    respectDnd = respectDnd,
                    nagIntervalMinutes = nagInterval,
                    nagStopAfterMinutes = nagStopAfter,
                    deleteAfterDismissed = deleteAfterDismissed,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                        Text("  Vibrate", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = respectDnd, onCheckedChange = { respectDnd = it })
                        Text("  Stay quiet during Do Not Disturb", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "The alert sound is the one picked in Chkt's settings on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NextButton(enabled = true) { step = Step.NAG }
                }

                Step.NAG -> {
                    Question("If you don't answer, should I keep reminding you?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = nagInterval == 0, onClick = { nagInterval = 0 }, label = { Text("Alert once") })
                        FilterChip(selected = nagInterval == 1, onClick = { nagInterval = 1 }, label = { Text("Every 1 min") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = nagInterval == 2, onClick = { nagInterval = 2 }, label = { Text("Every 2 min") })
                        FilterChip(selected = nagInterval == 5, onClick = { nagInterval = 5 }, label = { Text("Every 5 min") })
                    }
                    if (nagInterval > 0) {
                        Text("And give up after:", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 60, 120).forEach { minutes ->
                                FilterChip(
                                    selected = nagStopAfter == minutes,
                                    onClick = { nagStopAfter = minutes },
                                    label = { Text(if (minutes < 60) "$minutes min" else "${minutes / 60} hr" + if (minutes > 60) "s" else "") },
                                )
                            }
                        }
                    }
                    NextButton(enabled = true) { step = Step.EXTRAS }
                }

                Step.EXTRAS -> {
                    Question("A few last choices.")
                    if (lists.size > 1) {
                        Text("Which list does it belong in?", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lists.forEach { list ->
                                FilterChip(
                                    selected = chosenListId == list.id,
                                    onClick = { chosenListId = list.id },
                                    label = { Text(list.name) },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = deleteAfterDismissed, onCheckedChange = { deleteAfterDismissed = it })
                        Text("  Delete the reminder once dismissed", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = active, onCheckedChange = { active = it })
                        Text("  Active", style = MaterialTheme.typography.bodyMedium)
                    }
                    NextButton(enabled = true) { step = Step.CONFIRM }
                }

                Step.CONFIRM -> {
                    Question("All set?")
                    Text(summaryText(title, notes, date, time, rule, alertMode, preTone, locationTrigger, nagInterval, nagStopAfter, active),
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
    nagInterval: Int = 0,
    nagStopAfter: Int = 60,
    active: Boolean = true,
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
    val nagPart = if (nagInterval > 0) {
        " If you don't answer I'll try again every $nagInterval min and give up after " +
            (if (nagStopAfter < 60) "$nagStopAfter minutes." else "${nagStopAfter / 60} hour" + (if (nagStopAfter > 60) "s." else "."))
    } else ""
    val activePart = if (!active) "\n\nSaved switched off, flip it on when ready." else ""
    val notesPart = if (notes.isNotBlank()) "\n\nExtra words: $notes" else ""
    return "“$title”\n$whenPart$repeatPart.\n\n$alertPart$nagPart$notesPart$activePart"
}
