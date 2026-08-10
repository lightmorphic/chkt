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

    suspend fun ensureDefaultList(): ReminderList {
        val lists = db.lists()
        if (lists.count() == 0) {
            val list = ReminderList(name = "Reminders")
            lists.upsert(list)
            settings.setDefaultList(list.id)
            return list
        }
        // Any surviving list will do as a fallback target.
        return lists.changedSince(0).first { it.deletedAt == null }
    }

    suspend fun saveList(list: ReminderList) {
        db.lists().upsert(list.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteList(id: String) {
        db.lists().softDelete(id)
        // Orphan its reminders too, so they stop firing.
        db.reminders().changedSince(0)
            .filter { it.listId == id && it.deletedAt == null }
            .forEach { deleteReminder(it.id) }
    }

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
        val snoozed = reminder.copy(snoozedUntil = untilMillis, updatedAt = System.currentTimeMillis())
        db.reminders().upsert(snoozed)
        scheduler.schedule(snoozed)
    }

    /**
     * Called the moment an alarm fires: move a repeating reminder to its next
     * occurrence (or disable a one-off) and clear any snooze, so the next
     * alarm is armed even if the alert is never acted on.
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
