package org.chkt.app

import org.chkt.app.data.LocationTrigger
import org.chkt.app.data.Reminder
import org.chkt.app.domain.isSpentOneOff
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private fun reminder(
        enabled: Boolean = false,
        repeatRule: String = "",
        locationTrigger: LocationTrigger = LocationTrigger.NONE,
        deletedAt: Long? = null,
    ) = Reminder(
        title = "t", dueAt = 1_000L, enabled = enabled,
        repeatRule = repeatRule, locationTrigger = locationTrigger, deletedAt = deletedAt,
    )

    @Test
    fun `an answered one-off is history`() {
        assertTrue(reminder().isSpentOneOff())
    }

    @Test
    fun `live reminders are not history`() {
        assertFalse(reminder(enabled = true).isSpentOneOff())
    }

    @Test
    fun `a paused repeating reminder stays on the main list`() {
        assertFalse(reminder(repeatRule = "DAILY").isSpentOneOff())
    }

    @Test
    fun `a location reminder can come back on its own, so it is not history`() {
        assertFalse(reminder(locationTrigger = LocationTrigger.ARRIVE).isSpentOneOff())
    }

    @Test
    fun `deleted reminders are gone, not history`() {
        assertFalse(reminder(deletedAt = 5L).isSpentOneOff())
    }
}
