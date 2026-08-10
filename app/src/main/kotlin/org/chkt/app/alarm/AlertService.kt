package org.chkt.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.chkt.app.R
import org.chkt.app.data.AlertMode
import org.chkt.app.data.LogAction
import org.chkt.app.data.Reminder
import org.chkt.app.data.Repository
import org.chkt.app.tts.Speaker
import java.time.LocalTime

/**
 * Runs one alert from start to finish: ringtone, optional pre-tone, spoken
 * text, full-screen notification, and the Done/Snooze actions. Started by
 * AlarmReceiver; stops itself when the user responds or audio finishes.
 */
class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var speaker: Speaker? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DONE -> { handleDone(intent); return START_NOT_STICKY }
            ACTION_SNOOZE -> { handleSnooze(intent); return START_NOT_STICKY }
            ACTION_DISMISSED -> { handleDismissed(intent); return START_NOT_STICKY }
            ACTION_STOP_AUDIO -> { stopAudio(); return START_NOT_STICKY }
        }

        val id = intent?.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID)
        if (id == null) { stopSelf(); return START_NOT_STICKY }

        // Hold the CPU awake long enough to get audio going on a dozing phone.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chkt:alert").apply {
            acquire(120_000L)
        }

        scope.launch {
            val repo = Repository(applicationContext)
            val reminder = repo.db.reminders().byId(id)
            if (reminder == null || reminder.deletedAt != null || !reminder.enabled) {
                stopSelf(); return@launch
            }
            val firedDueAt = reminder.snoozedUntil ?: reminder.dueAt ?: System.currentTimeMillis()
            val quiet = repo.settings.quietHoursNow().contains(LocalTime.now())

            // Move repeating reminders to their next occurrence straight away,
            // so a crash or force-stop can never lose the next alarm.
            repo.fireAdvance(reminder)

            if (quiet || reminder.alertMode == AlertMode.NOTIFY_ONLY) {
                postNotification(reminder, firedDueAt, fullScreen = false, silentChannel = quiet)
                stopSelf()
                return@launch
            }

            startForeground(NOTIF_ID, buildNotification(reminder, firedDueAt, fullScreen = true, silentChannel = false))
            playAlert(reminder)
        }
        return START_NOT_STICKY
    }

    private fun playAlert(reminder: Reminder) {
        val speak = reminder.alertMode == AlertMode.RING_AND_SPEAK || reminder.alertMode == AlertMode.SPEAK_ONLY
        val ring = reminder.alertMode == AlertMode.RING_AND_SPEAK || reminder.alertMode == AlertMode.RING_ONLY

        val afterRing: () -> Unit = {
            if (speak) {
                if (reminder.preTone) playTone { speakText(reminder) } else speakText(reminder)
            } else {
                finishAfterDelay()
            }
        }
        if (ring) playRingtone(afterRing) else afterRing()
    }

    private fun playRingtone(onDone: () -> Unit) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        playUri(uri, onDone)
    }

    private fun playTone(onDone: () -> Unit) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        playUri(uri, onDone)
    }

    private fun playUri(uri: android.net.Uri?, onDone: () -> Unit) {
        if (uri == null) { onDone(); return }
        try {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlertService, uri)
                setOnCompletionListener { onDone() }
                setOnErrorListener { _, _, _ -> onDone(); true }
                prepare()
                start()
            }
        } catch (e: Exception) {
            onDone()
        }
    }

    private fun speakText(reminder: Reminder) {
        speaker = Speaker(this) { ready ->
            if (!ready) { finishAfterDelay(); return@Speaker }
            val text = reminder.title + if (reminder.notes.isNotBlank()) ". ${reminder.notes}" else ""
            speaker?.speak(text, reminder.id) { finishAfterDelay() }
        }
    }

    /** Audio finished — keep the notification up but let the service die soon. */
    private fun finishAfterDelay() {
        scope.launch {
            delay(1_000)
            stopAudio()
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun stopAudio() {
        player?.release(); player = null
        speaker?.shutdown(); speaker = null
    }

    private fun handleDone(intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val dueAt = intent.getLongExtra(EXTRA_DUE_AT, 0)
        stopAudio()
        scope.launch {
            Repository(applicationContext).logAction(id, dueAt, LogAction.DONE)
            cancelNotification()
            stopSelf()
        }
    }

    private fun handleSnooze(intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        stopAudio()
        scope.launch {
            Repository(applicationContext).snooze(id, System.currentTimeMillis() + minutes * 60_000L)
            cancelNotification()
            stopSelf()
        }
    }

    private fun handleDismissed(intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val dueAt = intent.getLongExtra(EXTRA_DUE_AT, 0)
        scope.launch {
            Repository(applicationContext).logAction(id, dueAt, LogAction.MISSED)
            stopSelf()
        }
    }

    private fun postNotification(reminder: Reminder, dueAt: Long, fullScreen: Boolean, silentChannel: Boolean) {
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        try {
            nm.notify(NOTIF_ID, buildNotification(reminder, dueAt, fullScreen, silentChannel))
        } catch (e: SecurityException) {
            // Notifications permission revoked; alarm audio already handled elsewhere.
        }
    }

    private fun cancelNotification() {
        androidx.core.app.NotificationManagerCompat.from(this).cancel(NOTIF_ID)
    }

    private fun buildNotification(reminder: Reminder, dueAt: Long, fullScreen: Boolean, silentChannel: Boolean): Notification {
        val channel = if (silentChannel) Notifications.CHANNEL_SILENT else Notifications.CHANNEL_ALARMS

        fun serviceAction(action: String, extra: Int? = null): PendingIntent {
            val i = Intent(this, AlertService::class.java)
                .setAction(action)
                .putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_DUE_AT, dueAt)
            if (extra != null) i.putExtra(EXTRA_SNOOZE_MINUTES, extra)
            return PendingIntent.getService(
                this, (reminder.id + action).hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val fullScreenIntent = PendingIntent.getActivity(
            this, reminder.id.hashCode(),
            Intent(this, AlertActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_DUE_AT, dueAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(reminder.notes.ifBlank { null })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(fullScreen)
            .setAutoCancel(true)
            .setContentIntent(fullScreenIntent)
            .setDeleteIntent(serviceAction(ACTION_DISMISSED))
            .addAction(0, getString(R.string.action_done), serviceAction(ACTION_DONE))
            .addAction(0, getString(R.string.action_snooze), serviceAction(ACTION_SNOOZE, 10))

        if (fullScreen) builder.setFullScreenIntent(fullScreenIntent, true)
        return builder.build()
    }

    override fun onDestroy() {
        stopAudio()
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 100
        const val ACTION_DONE = "org.chkt.app.DONE"
        const val ACTION_SNOOZE = "org.chkt.app.SNOOZE"
        const val ACTION_DISMISSED = "org.chkt.app.DISMISSED"
        const val ACTION_STOP_AUDIO = "org.chkt.app.STOP_AUDIO"
        const val EXTRA_DUE_AT = "due_at"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }
}
