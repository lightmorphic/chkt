package org.chkt.app.domain

import org.chkt.app.data.Reminder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When a repeating reminder's fire time has already passed, its next real
 * alert is the next occurrence (tomorrow, next week, ...), not the stale
 * past time still stored in dueAt until it's answered or nag-timed-out.
 * The home list sorts by this instead of the raw stored time, so a
 * reminder mid-nag doesn't stay pinned at the top of the list long after
 * its moment has gone. One-off reminders keep their raw time even when
 * overdue: there's no next occurrence to roll forward to, and a
 * still-unanswered one-off genuinely does need attention now.
 */
fun Reminder.nextAlertMillis(nowMillis: Long = System.currentTimeMillis()): Long? {
    val raw = snoozedUntil ?: dueAt ?: return null
    if (raw > nowMillis) return raw
    val rule = RepeatRule.decode(repeatRule)
    if (rule is RepeatRule.None) return raw
    val zone = ZoneId.systemDefault()
    val previous = ZonedDateTime.ofInstant(Instant.ofEpochMilli(raw), zone)
    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
    val next = rule.nextAfter(previous, now) ?: return raw
    return next.toInstant().toEpochMilli()
}
