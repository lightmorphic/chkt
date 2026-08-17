package org.chkt.app.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import org.chkt.app.backup.ExportImport
import org.chkt.app.data.QuietHours
import org.chkt.app.data.SyncConfig
import org.chkt.app.sync.SyncClient
import org.chkt.app.tts.Speaker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val quiet by repo.settings.quietHours.collectAsState(initial = QuietHours())
    val backupEnabled by repo.settings.backupEnabled.collectAsState(initial = false)
    val backupFolder by repo.settings.backupFolder.collectAsState(initial = null)
    val sync by repo.settings.syncConfig.collectAsState(initial = SyncConfig())

    var statusMessage by remember { mutableStateOf("") }

    val pickBackupFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            scope.launch {
                repo.settings.setBackup(true, uri.toString())
                org.chkt.app.backup.BackupScheduler.ensureScheduled(context)
            }
        }
    }

    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val result = ExportImport.exportJsonToUri(context, repo, uri)
            statusMessage = if (result) "Exported." else "Export failed."
        }
    }

    val exportMarkdown = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) scope.launch {
            val result = ExportImport.exportMarkdownToUri(context, repo, uri)
            statusMessage = if (result) "Exported." else "Export failed."
        }
    }

    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val count = ExportImport.importJsonFromUri(context, repo, uri)
            statusMessage = if (count >= 0) "Imported $count reminders." else "Import failed, is it a CHKT JSON export?"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton24(ChktIcon.Back, "Back", MaterialTheme.colorScheme.onSurface, onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard("Alarm reliability") {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val exactOk = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
                Text(
                    if (exactOk) "Exact alarms are allowed, reminders will fire on time."
                    else "Exact alarms are blocked. Reminders may be late until you allow them.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }) { Text("Allow exact alarms") }
                }
                // Android 14 can silently refuse full-screen alarm takeover for
                // sideloaded apps; without this the alarm still rings but stays
                // a quiet notification instead of filling the screen.
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val fullScreenOk = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
                if (!fullScreenOk) {
                    Text(
                        "Full-screen alarms are blocked. Alerts will only appear as notifications until you allow them.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData(Uri.parse("package:" + context.packageName))
                        )
                    }) { Text("Allow full-screen alarms") }
                }
                val recommendSherpa = remember { Speaker.shouldRecommendSherpa(context) }
                if (recommendSherpa) {
                    Text(
                        context.getString(org.chkt.app.R.string.tts_missing_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Speaker.SHERPA_FDROID_URL)))
                    }) { Text(context.getString(org.chkt.app.R.string.tts_get_sherpa)) }
                }
                OutlinedButton(onClick = {
                    try {
                        // No public Settings.ACTION_* constant for this exists;
                        // this is the action AOSP's own Settings app registers.
                        context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                    } catch (e: android.content.ActivityNotFoundException) {
                        // No system TTS settings screen on this ROM; nothing more we can do.
                    }
                }) { Text("Android voice engine settings") }
            }

            SettingsCard("Notification sound") {
                Text(
                    "The sound CHKT's notifications play. Applies to reminders that aren't set to Voice only.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                var soundLabel by remember {
                    mutableStateOf(ringtoneTitle(context, org.chkt.app.alarm.Notifications.soundUri(context)))
                }
                val pickSound = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                            result.data?.getParcelableExtra(
                                android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                        }
                        org.chkt.app.alarm.Notifications.setSoundUri(context, uri)
                        soundLabel = ringtoneTitle(context, uri)
                    }
                }
                OutlinedButton(onClick = {
                    val current = org.chkt.app.alarm.Notifications.soundUri(context)
                    val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                            android.media.RingtoneManager.TYPE_NOTIFICATION)
                        .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "CHKT notification sound")
                        .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                    pickSound.launch(intent)
                }) { Text(soundLabel) }
            }

            SettingsCard("Quiet hours") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = quiet.enabled, onCheckedChange = {
                        scope.launch { repo.settings.setQuietHours(quiet.copy(enabled = it)) }
                    })
                    Text("  During quiet hours, reminders arrive silently as notifications.", style = MaterialTheme.typography.bodySmall)
                }
                if (quiet.enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MinuteField("From", quiet.startMinutes) {
                            scope.launch { repo.settings.setQuietHours(quiet.copy(startMinutes = it)) }
                        }
                        MinuteField("To", quiet.endMinutes) {
                            scope.launch { repo.settings.setQuietHours(quiet.copy(endMinutes = it)) }
                        }
                    }
                }
            }

            SettingsCard("Backup") {
                Text(
                    if (backupEnabled && backupFolder != null) "Daily backup is on."
                    else "Daily backup is off. Pick a folder to turn it on, pair it with Syncthing or Nextcloud and your backups leave the phone too.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { pickBackupFolder.launch(null) }) { Text("Choose backup folder") }
            }

            SettingsCard("Export / import") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportFile.launch("chkt-export.json") }) { Text("Export JSON") }
                    OutlinedButton(onClick = { exportMarkdown.launch("chkt-export.md") }) { Text("Export Markdown") }
                }
                OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Import JSON") }
            }

            SettingsCard("Sync (optional)") {
                Text(
                    "Off by default. Point CHKT at your own CHKT Server to keep this phone and the web version matched.",
                    style = MaterialTheme.typography.bodySmall,
                )
                var server by remember(sync.serverUrl) { mutableStateOf(sync.serverUrl) }
                var key by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = sync.enabled, onCheckedChange = { enabled ->
                        scope.launch {
                            repo.settings.setSync(sync.copy(enabled = enabled, serverUrl = server, accessKey = if (key.isNotBlank()) key else sync.accessKey))
                            if (enabled) org.chkt.app.sync.SyncScheduler.ensureScheduled(context)
                            else org.chkt.app.sync.SyncScheduler.cancel(context)
                        }
                    })
                    Text("  Sync on")
                }
                if (sync.enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        IconButton24(ChktIcon.Tick, "Active", Color(0xFF2E8B6F)) {}
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (sync.lastSyncAt == 0L) "Active, hasn't synced yet"
                            else "Active, last synced " + relativeTime(sync.lastSyncAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E8B6F),
                        )
                    }
                }
                OutlinedTextField(
                    value = server, onValueChange = { server = it },
                    label = { Text("Server address (https://…)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text(if (sync.accessKey.isBlank()) "Access key" else "Access key (saved, leave blank to keep)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                var testing by remember { mutableStateOf(false) }
                var testResult by remember { mutableStateOf<org.chkt.app.sync.SyncClient.ConnectionTest?>(null) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = !testing, onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val effectiveKey = if (key.isNotBlank()) key else sync.accessKey
                            repo.settings.setSync(sync.copy(serverUrl = server, accessKey = effectiveKey))
                            testResult = SyncClient(context).testConnection(server, effectiveKey)
                            testing = false
                        }
                    }) { Text(if (testing) "Testing…" else "Test connection") }
                    if (testing) {
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                // Right under the button, not buried at the bottom of the
                // whole Settings screen, so it can't be mistaken for nothing
                // having happened.
                testResult?.let { result ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        if (result.ok) {
                            IconButton24(ChktIcon.Tick, "Connected", Color(0xFF2E8B6F)) {}
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            result.message,
                            color = if (result.ok) Color(0xFF2E8B6F) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            SettingsCard("App updates") {
                Text(
                    "You're on version " + org.chkt.app.update.Updater.installedVersionName(context) + ". Updates come from the CHKT project page; checking only happens when you ask (or daily, if you switch that on).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                var checking by remember { mutableStateOf(false) }
                var updateInfo by remember { mutableStateOf<org.chkt.app.update.Updater.UpdateInfo?>(null) }
                var updateMessage by remember { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !checking, onClick = {
                        checking = true; updateMessage = ""
                        scope.launch {
                            when (val result = org.chkt.app.update.Updater.check(context)) {
                                is org.chkt.app.update.Updater.CheckResult.UpToDate ->
                                    updateMessage = "You're up to date (${result.current})."
                                is org.chkt.app.update.Updater.CheckResult.UpdateAvailable -> {
                                    updateInfo = result.info
                                    updateMessage = "Version ${result.info.version} is available."
                                }
                                is org.chkt.app.update.Updater.CheckResult.Failed ->
                                    updateMessage = result.message
                            }
                            checking = false
                        }
                    }) { Text(if (checking) "Checking…" else "Check for updates") }
                    updateInfo?.let { info ->
                        Button(onClick = {
                            updateMessage = "Downloading ${info.version}…"
                            scope.launch {
                                val error = org.chkt.app.update.Updater.downloadAndInstall(context, info)
                                updateMessage = error ?: "Android will now ask you to confirm the install."
                            }
                        }) { Text("Update to ${info.version}") }
                    }
                }
                if (updateMessage.isNotBlank()) {
                    Text(updateMessage, style = MaterialTheme.typography.bodyMedium)
                }
                val autoCheck by repo.settings.autoUpdateCheck.collectAsState(initial = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoCheck, onCheckedChange = { enabled ->
                        scope.launch {
                            repo.settings.setAutoUpdateCheck(enabled)
                            org.chkt.app.update.Updater.setAutoCheck(context, enabled)
                        }
                    })
                    Text("  Check once a day and tell me", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun MinuteField(label: String, minutes: Int, onChange: (Int) -> Unit) {
    var text by remember(minutes) {
        mutableStateOf("%02d:%02d".format(minutes / 60, minutes % 60))
    }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            Regex("^(\\d{1,2}):(\\d{2})$").find(v.trim())?.let { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h in 0..23 && min in 0..59) onChange(h * 60 + min)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(0.45f),
    )
}

private fun relativeTime(epochMillis: Long): String {
    val minutes = (System.currentTimeMillis() - epochMillis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}

private fun ringtoneTitle(context: Context, uri: Uri?): String {
    if (uri == null) return "Choose notification sound"
    val title = runCatching {
        android.media.RingtoneManager.getRingtone(context, uri)?.getTitle(context)
    }.getOrNull()
    return title ?: "Choose notification sound"
}
