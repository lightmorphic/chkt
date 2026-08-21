package org.chkt.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object Notifications {
    // Channels carry no sound of their own. A channel only sounds when a
    // notification first appears, which left nag re-alerts and alerts after
    // a snooze silent, and it sounds the moment the notification is posted,
    // which put the ding on top of the spoken reminder. AlertChime plays the
    // sound instead, so it lands before the voice, every single alert.
    //
    // The IDs keep their historical "_quiet" suffix: a channel's sound and
    // importance are fixed when it is created, so reusing the IDs that were
    // already silent is the only way existing installs get silent channels.
    const val CHANNEL_ALARMS = "alarms_quiet"
    const val CHANNEL_POLITE = "polite_quiet"
    const val CHANNEL_SILENT = "silent"
    const val CHANNEL_SERVICE = "service"

    // Retired sound-bearing channels, deleted on upgrade. The versioned IDs
    // existed because Android ignores a sound change to an existing channel,
    // so each new sound needed a new generation of channel.
    private const val CHANNEL_ALARMS_LEGACY = "alarms"
    private const val CHANNEL_POLITE_LEGACY = "polite"

    private const val PREFS = "chkt_channel_sound"
    private const val KEY_SOUND_URI = "sound_uri"
    private const val KEY_CHANNEL_GEN = "channel_gen"

    /** The user's chosen notification sound, read synchronously (plain
     * SharedPreferences, not DataStore) since it is read from an alarm that
     * has no coroutine scope to wait on. Null means "use the system default
     * notification sound". */
    fun soundUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SOUND_URI, null)
            ?: return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /** Null resets to the system default. Takes effect on the next alert —
     * no channel juggling needed, since AlertChime reads this each time. */
    fun setSoundUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOUND_URI, uri?.toString())
            .apply()
    }

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        deleteRetiredChannels(context, nm)

        val alarms = NotificationChannel(
            CHANNEL_ALARMS,
            context.getString(org.chkt.app.R.string.notif_channel_alarms),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
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
            CHANNEL_POLITE,
            context.getString(org.chkt.app.R.string.notif_channel_polite),
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

        nm.createNotificationChannels(listOf(alarms, silent, polite, service))
    }

    /** Clears out every channel CHKT no longer posts to, so system settings
     * don't offer a sound slider that changes nothing. Cheap and idempotent:
     * deleting a channel that isn't there does nothing. */
    private fun deleteRetiredChannels(context: Context, nm: NotificationManager) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        nm.deleteNotificationChannel(CHANNEL_ALARMS_LEGACY)
        nm.deleteNotificationChannel(CHANNEL_POLITE_LEGACY)
        // Every sound the user ever picked created one more generation.
        for (gen in 1..prefs.getInt(KEY_CHANNEL_GEN, 1)) {
            nm.deleteNotificationChannel("alarms_v$gen")
            nm.deleteNotificationChannel("polite_v$gen")
        }
        prefs.edit().remove(KEY_CHANNEL_GEN).apply()
    }
}
