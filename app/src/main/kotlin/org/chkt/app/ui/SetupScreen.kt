package org.chkt.app.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

/**
 * First-run setup: ask for everything the app needs to actually wake you up.
 * Reappears at launch while anything essential is missing; every row rechecks
 * itself when you come back from the system dialogs.
 */
object SetupCheck {
    fun notificationsOk(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun exactAlarmsOk(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun fullScreenOk(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
    }

    fun batteryOk(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun allEssentialGranted(context: Context): Boolean =
        notificationsOk(context) && exactAlarmsOk(context) &&
            fullScreenOk(context) && batteryOk(context)
}

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()

    // Recheck every permission each time the user returns from a system dialog.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsOk = remember(refresh) { SetupCheck.notificationsOk(context) }
    val exactOk = remember(refresh) { SetupCheck.exactAlarmsOk(context) }
    val fullScreenOk = remember(refresh) { SetupCheck.fullScreenOk(context) }
    var batteryOk by remember { mutableStateOf(SetupCheck.batteryOk(context)) }
    remember(refresh) { batteryOk = SetupCheck.batteryOk(context); 0 }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    var soundPicked by remember { mutableStateOf(false) }
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
            scope.launch { repo.settings.setAlertSound(uri?.toString()) }
            soundPicked = true
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Let Chkt wake you up properly", fontSize = 26.sp, lineHeight = 34.sp)
        Text(
            "Reminders that ring and speak need a few permissions. Without them, alerts arrive late, quietly, or not at all.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SetupRow(
            title = "Notifications",
            detail = "The alert itself. Nothing works without this.",
            granted = notificationsOk,
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        SetupRow(
            title = "Exact alarms",
            detail = "Fire at the minute you set, not whenever the system feels like it.",
            granted = exactOk,
        ) {
            if (Build.VERSION.SDK_INT >= 31) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:" + context.packageName))
                )
            }
        }

        SetupRow(
            title = "Full-screen alarms",
            detail = "The alert takes over the screen like an alarm clock instead of a quiet banner.",
            granted = fullScreenOk,
        ) {
            if (Build.VERSION.SDK_INT >= 34) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(Uri.parse("package:" + context.packageName))
                )
            }
        }

        SetupRow(
            title = "Unrestricted battery",
            detail = "Stops power saving from delaying or killing alarms.",
            granted = batteryOk,
        ) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + context.packageName))
            )
        }

        Card {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Alert sound", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (soundPicked) "Chosen." else "Optional. The sound alerts ring with; system default until you pick one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = {
                    pickSound.launch(
                        Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                                android.media.RingtoneManager.TYPE_NOTIFICATION)
                            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Chkt alert sound")
                    )
                }) { Text("Choose") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                if (SetupCheck.allEssentialGranted(context)) "All set, let's go" else "Continue anyway",
                fontSize = 18.sp,
            )
        }
        Text(
            "You can change any of this later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
    }
    }
}

@Composable
private fun SetupRow(title: String, detail: String, granted: Boolean, onAsk: () -> Unit) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton24(
                if (granted) ChktIcon.Tick else ChktIcon.Bell,
                if (granted) "Granted" else "Needed",
                if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            ) {}
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!granted) {
                Button(onClick = onAsk) { Text("Allow") }
            }
        }
    }
}
