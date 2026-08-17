package org.chkt.app.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.chkt.app.data.Repository
import org.chkt.app.domain.SnoozeDurations
import org.chkt.app.ui.theme.ChktTheme

/**
 * Full-screen alarm overlay: shows over the lock screen, turns the screen on,
 * and offers Done plus a spread of snooze lengths up to a day.
 */
class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOn()

        val reminderId = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: run { finish(); return }
        val dueAt = intent.getLongExtra(AlertService.EXTRA_DUE_AT, 0)

        setContent {
            ChktTheme {
                var title by remember { mutableStateOf("") }
                var notes by remember { mutableStateOf("") }
                var snoozeOptions by remember { mutableStateOf(SnoozeDurations.DEFAULT) }
                LaunchedEffect(reminderId) {
                    val repo = Repository(applicationContext)
                    val r = withContext(Dispatchers.IO) { repo.db.reminders().byId(reminderId) }
                    title = r?.title ?: ""
                    notes = r?.notes ?: ""
                    snoozeOptions = repo.settings.snoozeMinutes.first()
                }
                AlertScreen(
                    title = title,
                    notes = notes,
                    snoozeOptions = snoozeOptions,
                    onDone = { act(AlertService.ACTION_DONE, reminderId, dueAt) },
                    onSnooze = { minutes -> act(AlertService.ACTION_SNOOZE, reminderId, dueAt, minutes) },
                )
            }
        }
    }

    private fun act(action: String, reminderId: String, dueAt: Long, snoozeMinutes: Int? = null) {
        val i = Intent(this, AlertService::class.java)
            .setAction(action)
            .putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            .putExtra(AlertService.EXTRA_DUE_AT, dueAt)
        if (snoozeMinutes != null) i.putExtra(AlertService.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        startService(i)
        finish()
    }

    private fun turnScreenOn() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        }
    }
}

private val AlertYellow = Color(0xFFFBC711)
private val AlertNavy = Color(0xFF111827)

@Composable
private fun AlertScreen(
    title: String,
    notes: String,
    snoozeOptions: List<Int>,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
) {
    // The alert is deliberately unmissable: bright brand yellow with navy
    // controls, nothing like an ordinary app screen.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlertYellow)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 34.sp, textAlign = TextAlign.Center, color = AlertNavy)
        if (notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(notes, fontSize = 18.sp, textAlign = TextAlign.Center, color = AlertNavy.copy(alpha = 0.75f))
        }
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AlertNavy, contentColor = AlertYellow),
        ) { Text("Done", fontSize = 22.sp) }
        Spacer(Modifier.height(24.dp))
        Text("Snooze", color = AlertNavy.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            snoozeOptions.take(3).forEach { minutes ->
                SnoozeButton(SnoozeDurations.format(minutes), minutes, onSnooze)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            snoozeOptions.drop(3).take(3).forEach { minutes ->
                SnoozeButton(SnoozeDurations.format(minutes), minutes, onSnooze)
            }
        }
    }
}

@Composable
private fun SnoozeButton(label: String, minutes: Int, onSnooze: (Int) -> Unit) {
    OutlinedButton(
        onClick = { onSnooze(minutes) },
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = AlertNavy),
        border = androidx.compose.foundation.BorderStroke(2.dp, AlertNavy),
    ) { Text(label) }
}
