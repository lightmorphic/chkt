package org.chkt.app.data

import android.content.Context
import org.chkt.app.alarm.AlarmScheduler
import org.chkt.app.domain.RepeatRule
import org.chkt.app.location.LocationReminders
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Single write path for reminder data. Every mutation goes through here so
 * alarm scheduling can never drift out of step with what's stored.
 */
class Repository(
    private val context: Context,
    val db: ChktDatabase = ChktDatabase.get(context),
    val settings: AppSettings = AppSettings(context),
) {
    private val scheduler = AlarmScheduler(context)

    suspend fun saveReminder(reminder: Reminder) {
        val stamped = reminder.copy(updatedAt = System.currentTimeMillis())
        db.reminders().upsert(stamped)
        scheduler.schedule(stamped)
        if (stamped.locationTrigger != LocationTrigger.NONE) {
            LocationReminders.registerAll(context)
        } else {
            LocationReminders.unregister(context, stamped.id)
        }
    }

    suspend fun deleteReminder(id: String) {
        db.reminders().softDelete(id)
        scheduler.cancel(id)
        LocationReminders.unregister(context, id)
    }

    suspend fun logAction(reminderId: String, dueAt: Long, action: LogAction) {
        db.logs().insert(CompletionLog(reminderId = reminderId, dueAt = dueAt, action = action))
    }

    suspend fun snooze(id: String, untilMillis: Long) {
        val reminder = db.reminders().byId(id) ?: return
        db.logs().insert(
            CompletionLog(reminderId = id, dueAt = reminder.dueAt ?: 0, action = LogAction.SNOOZED)
        )
        val snoozed = reminder.copy(snoozedUntil = untilMillis, nagStartedAt = null, updatedAt = System.currentTimeMillis())
        db.reminders().upsert(snoozed)
        scheduler.schedule(snoozed)
    }

    /**
     * Called when an alarm fires. Decides between three paths:
     *  - nagging off: move straight to the next occurrence (or disable a one-off)
     *  - nagging on, within the stop window: keep the occurrence live and arm
     *    the next re-alert
     *  - nagging on, stop window passed: give up, log it missed, move on
     * Returns true when the alert should actually sound this time.
     */
    suspend fun onFired(reminder: Reminder): Boolean {
        val now = System.currentTimeMillis()
        if (reminder.nagIntervalMinutes <= 0) {
            fireAdvance(reminder)
            return true
        }
        val startedAt = reminder.nagStartedAt
        if (startedAt == null) {
            val nagging = reminder.copy(nagStartedAt = now, updatedAt = now)
            db.reminders().upsert(nagging)
            scheduler.scheduleAt(nagging, now + reminder.nagIntervalMinutes * 60_000L)
            return true
        }
        if (now - startedAt >= reminder.nagStopAfterMinutes * 60_000L) {
            logAction(reminder.id, reminder.snoozedUntil ?: reminder.dueAt ?: now, LogAction.MISSED)
            fireAdvance(reminder)
            return false
        }
        scheduler.scheduleAt(reminder, now + reminder.nagIntervalMinutes * 60_000L)
        return true
    }

    /**
     * The user answered the alert (Done or dismissed it): log it, stop any
     * nagging, move to the next occurrence, and honour delete-after-dismissed.
     */
    suspend fun acknowledge(id: String, dueAt: Long, action: LogAction) {
        val reminder = db.reminders().byId(id) ?: return
        logAction(id, dueAt, action)
        if (reminder.deleteAfterDismissed) {
            deleteReminder(id)
            return
        }
        fireAdvance(reminder)
    }

    /**
     * Move a fired reminder to its next occurrence (or disable a one-off),
     * clearing any snooze and nag state.
     */
    suspend fun fireAdvance(reminder: Reminder) {
        val zone = ZoneId.systemDefault()
        val rule = RepeatRule.decode(reminder.repeatRule)
        val prev = reminder.dueAt?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        val next = if (prev != null) rule.nextAfter(prev, ZonedDateTime.now(zone)) else null
        val updated = reminder.copy(
            dueAt = next?.toInstant()?.toEpochMilli() ?: reminder.dueAt,
            enabled = if (next == null && reminder.locationTrigger == LocationTrigger.NONE) false else reminder.enabled,
            snoozedUntil = null,
            nagStartedAt = null,
            updatedAt = System.currentTimeMillis(),
        )
        db.reminders().upsert(updated)
        if (next != null) scheduler.schedule(updated) else scheduler.cancel(updated.id)
    }

    /** Re-arm every alarm; called after boot, app update, or time changes. */
    suspend fun rescheduleAll() {
        db.reminders().allSchedulable().forEach { scheduler.schedule(it) }
        LocationReminders.registerAll(context)
    }
}
