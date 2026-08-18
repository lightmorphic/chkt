package org.chkt.app

import org.chkt.app.data.Reminder
import org.chkt.app.domain.nextAlertMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderOrderingTest {
    private val zone = ZoneId.of("Europe/London")
    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun reminder(dueAt: Long?, repeatRule: String = "DAILY", snoozedUntil: Long? = null) =
        Reminder(title = "x", dueAt = dueAt, repeatRule = repeatRule, snoozedUntil = snoozedUntil)

    @Test
    fun `future due time sorts by itself`() {
        val due = millis(2026, 8, 19, 9, 0)
        val now = millis(2026, 8, 18, 10, 0)
        assertEquals(due, reminder(due).nextAlertMillis(now))
    }

    @Test
    fun `past due repeating reminder rolls forward to next occurrence`() {
        val due = millis(2026, 8, 18, 9, 0)
        val now = millis(2026, 8, 18, 10, 0)
        val expected = millis(2026, 8, 19, 9, 0)
        assertEquals(expected, reminder(due).nextAlertMillis(now))
    }

    @Test
    fun `still mid-nag past due reminder still rolls forward for sorting`() {
        // The stored dueAt hasn't advanced yet (still nagging), but the
        // list should place it at its real next occurrence, not the past.
        val due = millis(2026, 8, 18, 9, 0)
        val now = millis(2026, 8, 18, 9, 45)
        val expected = millis(2026, 8, 19, 9, 0)
        assertEquals(expected, reminder(due, repeatRule = "DAILY").nextAlertMillis(now))
    }

    @Test
    fun `past due one-off reminder keeps its raw overdue time`() {
        val due = millis(2026, 8, 18, 9, 0)
        val now = millis(2026, 8, 18, 10, 0)
        assertEquals(due, reminder(due, repeatRule = "").nextAlertMillis(now))
    }

    @Test
    fun `snoozed time takes priority over dueAt`() {
        val due = millis(2026, 8, 18, 9, 0)
        val snoozed = millis(2026, 8, 18, 9, 10)
        val now = millis(2026, 8, 18, 9, 5)
        assertEquals(snoozed, reminder(due, snoozedUntil = snoozed).nextAlertMillis(now))
    }

    @Test
    fun `no due date sorts null`() {
        assertNull(reminder(null).nextAlertMillis(millis(2026, 8, 18, 10, 0)))
    }
}
