package org.chkt.app.domain

import org.chkt.app.data.LocationTrigger
import org.chkt.app.data.Reminder

/**
 * A one-time reminder that has had its moment: no repeat rule and no
 * location trigger to bring it back, and switched off (which is what
 * answering a one-off does). These live in History rather than the main
 * list — visible to look back on, and reusable by giving them a new date —
 * so spent one-offs don't clutter what's coming up.
 */
fun Reminder.isSpentOneOff(): Boolean =
    !enabled &&
        deletedAt == null &&
        repeatRule.isBlank() &&
        locationTrigger == LocationTrigger.NONE
