package org.chkt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.chkt.app.data.LogAction
import java.time.Duration
import java.time.Instant

/**
 * Deliberately lightweight: how consistently reminders get done, nothing more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val repo = LocalRepository.current
    val thirtyDaysAgo = remember { Instant.now().minus(Duration.ofDays(30)).toEpochMilli() }
    val entries by repo.db.logs().observeSince(thirtyDaysAgo).collectAsState(initial = emptyList())

    val done = entries.count { it.action == LogAction.DONE }
    val missed = entries.count { it.action == LogAction.MISSED }
    val snoozed = entries.count { it.action == LogAction.SNOOZED }
    val acted = done + missed
    val rate = if (acted > 0) (done * 100) / acted else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Last 30 days") },
                navigationIcon = { IconButton24(ChktIcon.Back, "Back", MaterialTheme.colorScheme.onSurface, onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("Completed", done.toString())
            StatCard("Missed", missed.toString())
            StatCard("Snoozed", snoozed.toString())
            StatCard(
                "Completion rate",
                rate?.let { "$it%" } ?: "No data yet",
            )
            Text(
                "Counted from what you tap: Done counts as completed, a swiped-away alert counts as missed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
