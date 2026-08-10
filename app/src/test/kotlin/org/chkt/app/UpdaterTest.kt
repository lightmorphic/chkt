package org.chkt.app

import org.chkt.app.update.Updater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun `newer versions are detected`() {
        assertTrue(Updater.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updater.isNewer("1.1.0", "1.0.9"))
        assertTrue(Updater.isNewer("2.0.0", "1.9.9"))
        assertTrue(Updater.isNewer("1.0.10", "1.0.9"))
    }

    @Test
    fun `same or older versions are not`() {
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("0.9.9", "1.0.0"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun `garbage never claims to be newer`() {
        assertFalse(Updater.isNewer("", "1.0.0"))
        assertFalse(Updater.isNewer("banana", "1.0.0"))
    }
}
