package org.chkt.app

import org.chkt.app.data.QuietHours
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** Quiet hours spanning midnight are the classic off-by-one habitat:
 *  22:00–07:00 must cover 23:00 and 06:59 but not 21:59 or 07:00. */
class QuietHoursTest {
    private val overnight = QuietHours(enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60)
    private val daytime = QuietHours(enabled = true, startMinutes = 9 * 60, endMinutes = 17 * 60)

    @Test fun `overnight window covers both sides of midnight`() {
        assertTrue(overnight.contains(LocalTime.of(23, 0)))
        assertTrue(overnight.contains(LocalTime.of(0, 0)))
        assertTrue(overnight.contains(LocalTime.of(6, 59)))
    }

    @Test fun `overnight window has exact edges`() {
        assertTrue(overnight.contains(LocalTime.of(22, 0)))   // start inclusive
        assertFalse(overnight.contains(LocalTime.of(7, 0)))   // end exclusive
        assertFalse(overnight.contains(LocalTime.of(21, 59)))
        assertFalse(overnight.contains(LocalTime.of(12, 0)))
    }

    @Test fun `daytime window behaves the same way`() {
        assertTrue(daytime.contains(LocalTime.of(9, 0)))
        assertTrue(daytime.contains(LocalTime.of(16, 59)))
        assertFalse(daytime.contains(LocalTime.of(17, 0)))
        assertFalse(daytime.contains(LocalTime.of(8, 59)))
    }

    @Test fun `disabled never contains anything`() {
        val off = QuietHours(enabled = false, startMinutes = 0, endMinutes = 24 * 60)
        assertFalse(off.contains(LocalTime.NOON))
    }
}
