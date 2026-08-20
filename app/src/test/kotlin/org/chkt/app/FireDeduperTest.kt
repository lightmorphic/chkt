package org.chkt.app

import org.chkt.app.alarm.FireDeduper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FireDeduperTest {
    private val window = 10_000L

    @Test
    fun `first firing is not a duplicate`() {
        assertFalse(FireDeduper(window).isDuplicate("r1:1000", 0))
    }

    @Test
    fun `redelivery inside the window is a duplicate`() {
        val deduper = FireDeduper(window)
        deduper.isDuplicate("r1:1000", 0)
        assertTrue(deduper.isDuplicate("r1:1000", 3_000))
    }

    @Test
    fun `nag re-alert with the same key fires again after the window`() {
        // Nag re-alerts reuse the occurrence's key because dueAt doesn't
        // change while nagging — the regression in 1.0.11 swallowed these.
        val deduper = FireDeduper(window)
        deduper.isDuplicate("r1:1000", 0)
        assertFalse(deduper.isDuplicate("r1:1000", 120_000))
    }

    @Test
    fun `different reminders never block each other`() {
        val deduper = FireDeduper(window)
        deduper.isDuplicate("r1:1000", 0)
        assertFalse(deduper.isDuplicate("r2:1000", 0))
    }

    @Test
    fun `duplicate check does not extend the window`() {
        // A duplicate at t=9s must not refresh the entry: the original
        // firing's window still ends at 10s, so a fire at 12s is genuine.
        val deduper = FireDeduper(window)
        deduper.isDuplicate("r1:1000", 0)
        assertTrue(deduper.isDuplicate("r1:1000", 9_000))
        assertFalse(deduper.isDuplicate("r1:1000", 12_000))
    }
}
