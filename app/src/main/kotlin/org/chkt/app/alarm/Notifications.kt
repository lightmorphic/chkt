package org.chkt.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri

object Notifications {
    // Sound-bearing channels are versioned (see channelAlarms/channelPolite
    // below) because Android un-deletes a channel with its ORIGINAL settings
    // if you recreate the same ID after deleting it — silently ignoring
    // whatever new NotificationChannel you pass in. A fresh ID is the only
    // way a sound change actually takes effect, each time it changes.
    private const val CHANNEL_ALARMS_LEGACY = "alarms"
    private const val CHANNEL_POLITE_LEGACY = "polite"

    const val CHANNEL_SILENT = "silent"
    const val CHANNEL_SERVICE = "service"
    // Same prominence/vibration/DND behaviour as their sound-bearing twins,
    // but no notification sound — for Voice-only reminders, where the
    // spoken title is the alert and a ding on top would be redundant. Their
    // settings never change, so no versioning needed.
    const val CHANNEL_ALARMS_QUIET = "alarms_quiet"
    const val CHANNEL_POLITE_QUIET = "polite_quiet"

    private const val PREFS = "chkt_channel_sound"
    private const val KEY_SOUND_URI = "sound_uri"
    private const val KEY_CHANNEL_GEN = "channel_gen"
    private const val KEY_MIGRATED_LEGACY = "migrated_legacy_channels"

    private fun channelGen(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CHANNEL_GEN, 1)

    /** The sound-bearing alarm/polite channel IDs for the CURRENT sound.
     * Always use these, never the bare "alarms"/"polite" legacy IDs. */
    fun channelAlarms(context: Context) = "alarms_v" + channelGen(context)
    fun channelPolite(context: Context) = "polite_v" + channelGen(context)

    /** The user's chosen notification sound, read synchronously (plain
     * SharedPreferences, not DataStore) since channel creation happens in
     * Application.onCreate() before any coroutine scope exists. Null means
     * "use the system default notification sound". */
    fun soundUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SOUND_URI, null)
            ?: return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /** Null resets to the system default. Bumps the channel generation so
     * the next createChannels() call actually creates fresh, differently-
     * sounding channels instead of hitting the immutable-channel wall. */
    fun setSoundUri(context: Context, uri: Uri?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldGen = channelGen(context)
        prefs.edit()
            .putString(KEY_SOUND_URI, uri?.toString())
            .putInt(KEY_CHANNEL_GEN, oldGen + 1)
            .apply()
        createChannels(context)
        // Tidy up: the previous generation's channels are now orphaned.
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel("alarms_v$oldGen")
        nm.deleteNotificationChannel("polite_v$oldGen")
    }

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // One-time cleanup: installs from before channels were versioned
        // created plain "alarms"/"polite" with no sound. Deleting them here
        // just removes the dead entries from system settings — it does NOT
        // fix their sound (that's what versioning is for); the real fix is
        // that channelAlarms()/channelPolite() never return these IDs.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_MIGRATED_LEGACY, false)) {
            nm.deleteNotificationChannel(CHANNEL_ALARMS_LEGACY)
            nm.deleteNotificationChannel(CHANNEL_POLITE_LEGACY)
            prefs.edit().putBoolean(KEY_MIGRATED_LEGACY, true).apply()
        }

        val sound = soundUri(context)

        val alarms = NotificationChannel(
            channelAlarms(context),
            context.getString(org.chkt.app.R.string.notif_channel_alarms),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // Bypasses DND, so the sound needs USAGE_ALARM to actually play
            // through it rather than being silenced alongside it.
            setSound(sound, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            enableVibration(true)
            setBypassDnd(true)
        }

        val silent = NotificationChannel(
            CHANNEL_SILENT,
            context.getString(org.chkt.app.R.string.notif_channel_silent),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { setSound(null, null) }

        // For reminders set to respect Do Not Disturb: same prominence, but
        // no DND bypass, so the system can keep them quiet.
        val polite = NotificationChannel(
            channelPolite(context),
            context.getString(org.chkt.app.R.string.notif_channel_polite),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(sound, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            enableVibration(true)
        }

        val alarmsQuiet = NotificationChannel(
            CHANNEL_ALARMS_QUIET,
            context.getString(org.chkt.app.R.string.notif_channel_alarms_quiet),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(true)
            setBypassDnd(true)
        }

        val politeQuiet = NotificationChannel(
            CHANNEL_POLITE_QUIET,
            context.getString(org.chkt.app.R.string.notif_channel_polite_quiet),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(true)
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(org.chkt.app.R.string.notif_channel_service),
            NotificationManager.IMPORTANCE_LOW,
        )

        nm.createNotificationChannels(listOf(alarms, silent, polite, alarmsQuiet, politeQuiet, service))
    }
}
