package org.chkt.app.domain

import org.chkt.app.data.Reminder

/**
 * A reminder that has ended: switched off and not deleted. That covers a
 * one-off that fired (answering one switches it off), a repeating
 * reminder whose run is over, and anything turned off by hand. Ended
 * reminders live in History rather than the main list — visible to look
 * back on, and reusable by switching back on with a new date — so they
 * don't clutter what's coming up.
 */
fun Reminder.isEnded(): Boolean = !enabled && deletedAt == null
