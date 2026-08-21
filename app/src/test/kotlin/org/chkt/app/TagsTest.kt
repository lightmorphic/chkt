package org.chkt.app

import org.chkt.app.domain.normalizeTags
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tags are lowercase, trimmed and unique — the same rule the server applies,
 *  so a reminder edited on either side comes out looking the same. */
class TagsTest {
    @Test fun `case is flattened`() {
        assertEquals("cal", normalizeTags("Cal"))
        assertEquals("cal", normalizeTags("CAL"))
    }

    @Test fun `spacing is tidied and order kept`() {
        assertEquals("home, chores", normalizeTags("  home ,chores  "))
    }

    @Test fun `duplicates collapse, including ones that differed only by case`() {
        assertEquals("cal", normalizeTags("cal, Cal, CAL"))
        assertEquals("home, cal", normalizeTags("home, cal, home"))
    }

    @Test fun `empty entries are dropped`() {
        assertEquals("", normalizeTags(""))
        assertEquals("", normalizeTags(" , , "))
        assertEquals("home", normalizeTags("home,,"))
    }
}
