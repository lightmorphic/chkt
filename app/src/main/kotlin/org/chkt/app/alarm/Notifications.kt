package org.chkt.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_ALARMS = "alarms"
    const val CHANNEL_SILENT = "silent"
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

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(org.chkt.app.R.string.notif_channel_service),
            NotificationManager.IMPORTANCE_LOW,
        )

        nm.createNotificationChannels(listOf(alarms, silent, service))
    }
}
