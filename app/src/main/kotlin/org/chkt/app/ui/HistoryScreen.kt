package org.chkt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.chkt.app.data.Reminder
import org.chkt.app.domain.tagList
import org.chkt.app.domain.isEnded

/**
 * Where reminders go once they've ended — answered one-offs and
 * switched-off repeats alike. They stay out of the main list, but nothing
 * is lost: look back over what's done, or tap one to give it a new date
 * and bring it back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onReuse: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val all by repo.db.reminders().observeAll().collectAsState(initial = emptyList())
    val spent = remember(all) {
        all.filter { it.isEnded() }
            .sortedByDescending { it.dueAt ?: it.updatedAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = { IconButton24(ChktIcon.Back, "Back", MaterialTheme.colorScheme.onSurface, onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Reminders land here once they've ended — one-times that happened and repeats you've switched off. Tap one to give it a new date and bring it back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(spent, key = { it.id }) { reminder ->
                HistoryRow(
                    reminder = reminder,
                    onReuse = { onReuse(reminder.id) },
                    onDelete = { scope.launch { repo.deleteReminder(reminder.id) } },
                )
            }
            if (spent.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    reminder: Reminder,
    onReuse: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onReuse, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(reminder.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    describeWhen(reminder).ifBlank { "No date" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (reminder.tagList().isNotEmpty()) {
                    Text(
                        reminder.tagList().joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton24(ChktIcon.Delete, "Delete ${reminder.title}", MaterialTheme.colorScheme.onSurfaceVariant, onClick = onDelete)
        }
    }
}
