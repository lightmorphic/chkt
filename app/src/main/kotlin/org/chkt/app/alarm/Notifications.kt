package org.chkt.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_ALARMS = "alarms"
    const val CHANNEL_SILENT = "silent"
    const val CHANNEL_POLITE = "polite"
    const val CHANNEL_SERVICE = "service"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarms = NotificationChannel(
            CHANNEL_ALARMS,
            context.getString(org.chkt.app.R.string.notif_channel_alarms),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // Sound is played by AlertService on the alarm stream (which DND
            // lets through by default); the channel itself stays silent so the
            // two never overlap.
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
}
