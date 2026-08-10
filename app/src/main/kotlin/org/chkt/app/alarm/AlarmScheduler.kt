package org.chkt.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.chkt.app.data.Reminder

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Arm (or re-arm) the alarm for a reminder. Uses setAlarmClock, the one
     * scheduling mode Android treats as sacred: it fires exactly on time, in
     * Doze, with battery saver on, and shows the alarm icon in the status bar.
     */
    fun schedule(reminder: Reminder) {
        cancel(reminder.id)
        if (!reminder.enabled || reminder.deletedAt != null) return
        val fireAt = reminder.snoozedUntil ?: reminder.dueAt ?: return
        if (fireAt <= System.currentTimeMillis()) return
        if (!canScheduleExact()) return

        val showIntent = PendingIntent.getActivity(
            context, reminder.id.hashCode(),
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(fireAt, showIntent),
            firePendingIntent(reminder.id),
        )
    }

    fun cancel(reminderId: String) {
        alarmManager.cancel(firePendingIntent(reminderId))
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    private fun firePendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
            .setData(android.net.Uri.parse("chkt://reminder/$reminderId"))
        return PendingIntent.getBroadcast(
            context, reminderId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "org.chkt.app.FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
