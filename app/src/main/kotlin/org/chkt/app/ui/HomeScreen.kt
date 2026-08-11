package org.chkt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.chkt.app.data.Reminder

/**
 * Opening the app shows what's coming: every reminder in time order, with
 * tag chips to narrow things down. Tags replace lists — a reminder can wear
 * any number of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onEdit: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val reminders by repo.db.reminders().observeAll().collectAsState(initial = emptyList())
    var activeTag by remember { mutableStateOf<String?>(null) }

    val allTags = remember(reminders) {
        reminders.flatMap { it.tagList() }.distinct().sorted()
    }
    val shown = if (activeTag == null) reminders
    else reminders.filter { activeTag in it.tagList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChktLogo()
                        Text("  CHKT")
                    }
                },
                actions = {
                    val tint = MaterialTheme.colorScheme.onSurface
                    IconButton24(ChktIcon.Stats, "Statistics", tint, onClick = onOpenStats)
                    IconButton24(ChktIcon.Settings, "Settings", tint, onClick = onOpenSettings)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(null) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                IconButton24(ChktIcon.Add, "Add reminder", MaterialTheme.colorScheme.onPrimary) { onEdit(null) }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (allTags.isNotEmpty()) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = activeTag == null, onClick = { activeTag = null },
                            label = { Text("All") })
                    }
                    items(allTags) { tag ->
                        FilterChip(selected = activeTag == tag,
                            onClick = { activeTag = if (activeTag == tag) null else tag },
                            label = { Text(tag) })
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        onOpen = { onEdit(reminder.id) },
                        onToggle = { enabled ->
                            scope.launch { repo.saveReminder(reminder.copy(enabled = enabled)) }
                        },
                        onDelete = { scope.launch { repo.deleteReminder(reminder.id) } },
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            "Nothing coming up. Tap + to set a reminder.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Active state as a plain circle: filled grey when active, outline when off. */
@Composable
private fun ActiveCircle(active: Boolean, onToggle: () -> Unit) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onToggle)
            .padding(10.dp)
            .semantics {
                contentDescription =
                    if (active) "Active, tap to switch off" else "Off, tap to switch on"
            },
    ) {
        if (active) {
            drawCircle(color = grey)
        } else {
            drawCircle(color = grey, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

fun Reminder.tagList(): List<String> =
    tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

private val dueFormat = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

fun describeWhen(reminder: Reminder): String {
    val parts = mutableListOf<String>()
    reminder.dueAt?.let {
        parts += dueFormat.format(
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()))
    }
    when (val rule = org.chkt.app.domain.RepeatRule.decode(reminder.repeatRule)) {
        org.chkt.app.domain.RepeatRule.None -> {}
        org.chkt.app.domain.RepeatRule.Daily -> parts += "daily"
        is org.chkt.app.domain.RepeatRule.Weekly -> parts += "weekly (" + rule.days.sortedBy { it.value }
            .joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) } + ")"
        is org.chkt.app.domain.RepeatRule.Monthly -> parts += if (rule.last) "monthly (last day)" else "monthly (day ${rule.dayOfMonth})"
        is org.chkt.app.domain.RepeatRule.Yearly -> parts += "yearly"
        is org.chkt.app.domain.RepeatRule.Every -> parts += "every " + rule.interval.toMinutes().let { m ->
            when {
                m % (7 * 24 * 60) == 0L -> "${m / (7 * 24 * 60)} wk"
                m % (24 * 60) == 0L -> "${m / (24 * 60)} days"
                m % 60 == 0L -> "${m / 60} hrs"
                else -> "$m min"
            }
        }
    }
    if (reminder.locationTrigger != org.chkt.app.data.LocationTrigger.NONE) {
        parts += if (reminder.locationTrigger == org.chkt.app.data.LocationTrigger.ARRIVE) "on arrival" else "on leaving"
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                val whenText = describeWhen(reminder)
                if (whenText.isNotBlank()) {
                    Text(whenText, style = MaterialTheme.typography.bodySmall, color = tint)
                }
                val tagsText = reminder.tagList()
                if (tagsText.isNotEmpty()) {
                    Text(
                        tagsText.joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            ActiveCircle(active = reminder.enabled, onToggle = { onToggle(!reminder.enabled) })
            if (confirmingDelete) {
                IconButton24(ChktIcon.Tick, "Confirm delete", MaterialTheme.colorScheme.error, onClick = onDelete)
            } else {
                IconButton24(ChktIcon.Delete, "Delete reminder", tint) { confirmingDelete = true }
            }
        }
    }
}
