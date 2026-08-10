package org.chkt.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.chkt.app.data.LocationTrigger
import org.chkt.app.data.Reminder
import org.chkt.app.domain.RepeatRule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    listId: String,
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val reminders by repo.db.reminders().observeForList(listId).collectAsState(initial = emptyList())
    val listName by produceState(initialValue = "") {
        value = repo.db.lists().byId(listId)?.name ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listName) },
                navigationIcon = {
                    IconButton24(ChktIcon.Back, "Back", MaterialTheme.colorScheme.onSurface, onClick = onBack)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null) }) {
                IconButton24(ChktIcon.Add, "Add reminder", MaterialTheme.colorScheme.onPrimaryContainer) { onEdit(null) }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(reminders, key = { it.id }) { reminder ->
                ReminderRow(
                    reminder = reminder,
                    onOpen = { onEdit(reminder.id) },
                    onToggle = { enabled ->
                        scope.launch { repo.saveReminder(reminder.copy(enabled = enabled)) }
                    },
                    onDelete = { scope.launch { repo.deleteReminder(reminder.id) } },
                )
            }
        }
    }
}

private val dueFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

fun describeWhen(reminder: Reminder): String {
    val parts = mutableListOf<String>()
    reminder.dueAt?.let {
        parts += dueFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }
    when (val rule = RepeatRule.decode(reminder.repeatRule)) {
        RepeatRule.None -> {}
        RepeatRule.Daily -> parts += "daily"
        is RepeatRule.Weekly -> parts += "weekly (" + rule.days.sortedBy { it.value }
            .joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) } + ")"
        is RepeatRule.Monthly -> parts += if (rule.last) "monthly (last day)" else "monthly (day ${rule.dayOfMonth})"
        is RepeatRule.Yearly -> parts += "yearly"
        is RepeatRule.Every -> parts += "every " + rule.interval.toMinutes().let { m ->
            when {
                m % (7 * 24 * 60) == 0L -> "${m / (7 * 24 * 60)} wk"
                m % (24 * 60) == 0L -> "${m / (24 * 60)} days"
                m % 60 == 0L -> "${m / 60} hrs"
                else -> "$m min"
            }
        }
    }
    if (reminder.locationTrigger != LocationTrigger.NONE) {
        parts += if (reminder.locationTrigger == LocationTrigger.ARRIVE) "on arrival" else "on leaving"
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
            }
            Switch(checked = reminder.enabled, onCheckedChange = onToggle)
            if (confirmingDelete) {
                IconButton24(ChktIcon.Tick, "Confirm delete", MaterialTheme.colorScheme.error, onClick = onDelete)
            } else {
                IconButton24(ChktIcon.Delete, "Delete reminder", tint) { confirmingDelete = true }
            }
        }
    }
}
