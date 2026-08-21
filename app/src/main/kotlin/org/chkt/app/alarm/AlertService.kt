package org.chkt.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.chkt.app.R
import org.chkt.app.data.AlertMode
import org.chkt.app.data.LogAction
import org.chkt.app.data.Reminder
import org.chkt.app.data.Repository
import org.chkt.app.tts.Speaker
import java.time.LocalTime

/**
 * Runs one alert from start to finish: spoken text and/or a full-screen
 * notification, and the Done/Snooze actions. Started by AlarmReceiver;
 * stops itself when the user responds or audio finishes.
 */
class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speaker: Speaker? = null
    private var chime: AlertChime? = null
    private var wakeLock: PowerManager.WakeLock? = null
    /** Bumped by every new alert and by Done/Snooze, so an alert still
     * mid-sound can tell that it has been superseded and shut up. */
    private var alertGen = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DONE -> { handleDone(intent); return START_NOT_STICKY }
            ACTION_SNOOZE -> { handleSnooze(intent); return START_NOT_STICKY }
            ACTION_DISMISSED -> { handleDismissed(intent); return START_NOT_STICKY }
        }

        val id = intent?.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID)
        if (id == null) { stopSelf(); return START_NOT_STICKY }

        // We were started with startForegroundService, so startForeground
        // must happen promptly on EVERY path — including the ones that
        // decide not to alert — or Android kills the app with a
        // ForegroundServiceDidNotStartInTime crash. This placeholder is
        // low-importance and silent; the real alert notification replaces
        // or follows it.
        startForeground(SERVICE_NOTIF_ID, placeholderNotification())

        // Hold the CPU awake long enough to get audio going on a dozing
        // phone. Release any lock a concurrent start left behind first, or
        // it would leak until its timeout.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chkt:alert").apply {
            acquire(120_000L)
        }

        val gen = ++alertGen

        scope.launch {
            val repo = Repository(applicationContext)
            val reminder = repo.db.reminders().byId(id)
            if (reminder == null || reminder.deletedAt != null || !reminder.enabled) {
                stopQuietly(); return@launch
            }
            val firedDueAt = reminder.snoozedUntil ?: reminder.dueAt ?: System.currentTimeMillis()

            // Key on the id alone, NOT id+dueAt: onFired advances dueAt, so
            // a duplicate delivery landing after the first one commits reads
            // the advanced time, gets a different key, and would pass. The
            // window is far below the shortest nag interval (10s vs 1 min),
            // so no legitimate re-alert of the same reminder can collide.
            if (deduper.isDuplicate(id, System.currentTimeMillis())) {
                stopQuietly(); return@launch
            }

            val quiet = repo.settings.quietHoursNow().contains(LocalTime.now())

            // Handles nag re-alerts and arms whatever alarm comes next, so a
            // crash or force-stop can never lose the schedule.
            val shouldAlert = repo.onFired(reminder)
            if (!shouldAlert) { stopQuietly(); return@launch }

            if (quiet) {
                postNotification(reminder, firedDueAt, fullScreen = false, silentChannel = true)
                stopQuietly()
                return@launch
            }

            AlertLog.log(
                applicationContext,
                "alert gen=$gen mode=${reminder.alertMode} nag=${if (reminder.nagStartedAt != null) "re-alert" else "first"} \"${reminder.title.take(20)}\"",
            )

            // Notification-and-speak shows the full-screen alert; the other
            // modes just leave a banner, no full-screen popup.
            val fullScreen = reminder.alertMode == AlertMode.NOTIFY_AND_SPEAK
            startForeground(NOTIF_ID, buildNotification(reminder, firedDueAt, fullScreen = fullScreen, silentChannel = false))
            cancelPlaceholder()
            if (reminder.vibrate) vibrate()
            playAlert(reminder, gen)
        }
        return START_NOT_STICKY
    }

    private fun vibrate() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        val pattern = longArrayOf(0, 400, 250, 400, 250, 400)
        vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
    }

    /**
     * One alert, always in the same order: the notification sound first,
     * then the spoken title once that sound has actually finished. The
     * speech engine warms up while the sound plays — starting it makes no
     * noise — so the voice follows promptly without ever overlapping.
     *
     * Every alert runs this, so a nag re-alert or an alert after a snooze
     * sounds exactly like the first one did.
     */
    private suspend fun playAlert(reminder: Reminder, gen: Int) {
        if (gen != alertGen) return
        val speaks = reminder.alertMode != AlertMode.NOTIFY_ONLY
        // Voice-only alerts skip the ding — the spoken title is the alert.
        val dings = reminder.alertMode != AlertMode.SPEAK_ONLY

        // Nothing from a previous alert carries over into this one.
        stopAudio()
        val engineReady = if (speaks) CompletableDeferred<Boolean>() else null
        if (engineReady != null) {
            speaker = Speaker(this, respectDnd = reminder.respectDnd) { engineReady.complete(it) }
        }

        if (dings) {
            val sound = AlertChime { event -> AlertLog.log(applicationContext, event) }
            chime = sound
            sound.play(this, respectDnd = reminder.respectDnd)
            chime = null
            if (gen != alertGen) { AlertLog.log(applicationContext, "superseded after chime"); return }
            if (speaks) delay(GAP_MS)
        }

        val ready = engineReady != null &&
            withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) { engineReady.await() } == true
        if (gen != alertGen) { AlertLog.log(applicationContext, "superseded before voice"); return }
        if (!ready) {
            if (speaks) AlertLog.log(applicationContext, "voice skipped: engine not ready")
            finishAfterDelay(gen); return
        }
        // Notes show on the alert screen and in the notification, but
        // aren't spoken — they're often longer free text, not meant to
        // be read aloud the way the title is.
        AlertLog.log(applicationContext, "voice speaking")
        speaker?.speak(reminder.title, reminder.id) {
            AlertLog.log(applicationContext, "voice done")
            finishAfterDelay(gen)
        }
    }

    /** Audio finished, keep the notification up but let the service die soon.
     * A superseded alert bows out silently instead, leaving the service to
     * the alert that replaced it. */
    private fun finishAfterDelay(gen: Int) {
        if (gen != alertGen) return
        scope.launch {
            delay(1_000)
            if (gen != alertGen) return@launch
            stopAudio()
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    /** Leave foreground without keeping any placeholder notification, for
     * the paths that end without a (sounding) alert of their own. */
    private fun stopQuietly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cancelPlaceholder() {
        androidx.core.app.NotificationManagerCompat.from(this).cancel(SERVICE_NOTIF_ID)
    }

    private fun placeholderNotification(): Notification =
        NotificationCompat.Builder(this, Notifications.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_channel_service))
            .setSilent(true)
            .build()

    private fun stopAudio() {
        chime?.stop(); chime = null
        speaker?.shutdown(); speaker = null
    }

    private fun handleDone(intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val dueAt = intent.getLongExtra(EXTRA_DUE_AT, 0)
        alertGen++
        stopAudio()
        scope.launch {
            Repository(applicationContext).acknowledge(id, dueAt, LogAction.DONE)
            cancelNotification()
            stopSelf()
        }
    }

    private fun handleSnooze(intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        alertGen++
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
            Repository(applicationContext).acknowledge(id, dueAt, LogAction.MISSED)
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
        // Every alert channel is silent: the sound is played by AlertChime
        // so it can finish before the voice starts, and so it plays on every
        // alert rather than only the first one to raise this notification.
        val channel = when {
            silentChannel -> Notifications.CHANNEL_SILENT
            reminder.respectDnd -> Notifications.CHANNEL_POLITE
            else -> Notifications.CHANNEL_ALARMS
        }

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
            // A later update to this same notification (any re-post while
            // it's still up) must not replay the channel's sound/vibration.
            .setOnlyAlertOnce(true)
            .setContentIntent(fullScreenIntent)
            .setDeleteIntent(serviceAction(ACTION_DISMISSED))
            .addAction(0, getString(R.string.action_done), serviceAction(ACTION_DONE))
            // Snooze length is chosen when the alert happens, so this opens
            // the chooser rather than snoozing for a preset time.
            .addAction(0, getString(R.string.action_snooze), fullScreenIntent)

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
        /** The startForeground placeholder, distinct from NOTIF_ID so the
         * real alert notification starts fresh on its own channel. */
        const val SERVICE_NOTIF_ID = 101
        const val ACTION_DONE = "org.chkt.app.DONE"
        const val ACTION_SNOOZE = "org.chkt.app.SNOOZE"
        const val ACTION_DISMISSED = "org.chkt.app.DISMISSED"
        const val EXTRA_DUE_AT = "due_at"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        /** Breath between the ding and the voice, so they read as two
         * parts of one alert rather than running together. */
        private const val GAP_MS = 250L
        /** A speech engine that hasn't come up by now isn't going to. */
        private const val TTS_INIT_TIMEOUT_MS = 5_000L

        /** Shared across service instances: duplicate deliveries arrive as
         * separate start commands, often to a fresh instance. */
        private val deduper = FireDeduper()
    }
}
