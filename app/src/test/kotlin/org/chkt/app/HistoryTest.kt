package org.chkt.app

import org.chkt.app.data.LocationTrigger
import org.chkt.app.data.Reminder
import org.chkt.app.domain.isEnded
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
        assertTrue(reminder().isEnded())
    }

    @Test
    fun `a switched-off repeating reminder is history too`() {
        assertTrue(reminder(repeatRule = "DAILY").isEnded())
    }

    @Test
    fun `a switched-off location reminder is history too`() {
        assertTrue(reminder(locationTrigger = LocationTrigger.ARRIVE).isEnded())
    }

    @Test
    fun `live reminders are not history`() {
        assertFalse(reminder(enabled = true).isEnded())
        assertFalse(reminder(enabled = true, repeatRule = "DAILY").isEnded())
    }

    @Test
    fun `deleted reminders are gone, not history`() {
        assertFalse(reminder(deletedAt = 5L).isEnded())
    }
}
