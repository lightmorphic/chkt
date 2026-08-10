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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.chkt.app.data.ReminderList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onOpenList: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val lists by repo.db.lists().observeAll().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { repo.ensureDefaultList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chkt") },
                actions = {
                    val tint = MaterialTheme.colorScheme.onSurface
                    IconButton24(ChktIcon.Stats, "Statistics", tint, onClick = onOpenStats)
                    IconButton24(ChktIcon.Settings, "Settings", tint, onClick = onOpenSettings)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                IconButton24(ChktIcon.Add, "Add list", MaterialTheme.colorScheme.onPrimaryContainer) { adding = true }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lists, key = { it.id }) { list ->
                ListRow(
                    list = list,
                    onOpen = { onOpenList(list.id) },
                    onRename = { name -> scope.launch { repo.saveList(list.copy(name = name)) } },
                    onDelete = { scope.launch { repo.deleteList(list.id) } },
                )
            }
            if (adding) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("List name") },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            if (newName.isNotBlank()) {
                                scope.launch { repo.saveList(ReminderList(name = newName.trim())) }
                                newName = ""; adding = false
                            }
                        }) { Text("Add") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListRow(
    list: ReminderList,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(list.name) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.weight(1f))
                IconButton24(ChktIcon.Tick, "Save name", tint) {
                    if (name.isNotBlank()) { onRename(name.trim()); editing = false }
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(list.name, style = MaterialTheme.typography.titleMedium)
                }
                IconButton24(ChktIcon.Edit, "Rename list", tint) { editing = true }
                if (confirmingDelete) {
                    // Delete confirms inline: the bin swaps to a tick, no popup.
                    IconButton24(ChktIcon.Tick, "Confirm delete", MaterialTheme.colorScheme.error, onClick = onDelete)
                } else {
                    IconButton24(ChktIcon.Delete, "Delete list", tint) { confirmingDelete = true }
                }
            }
        }
    }
}
