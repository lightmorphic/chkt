package org.chkt.app.domain

import org.chkt.app.data.Reminder

/** A reminder's tags as a list, in the order they were written. */
fun Reminder.tagList(): List<String> =
    tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

/** Tags are lowercase, trimmed and unique, in the order given. Case was only
 * ever a way to end up with "Cal" and "cal": two tags that look the same in a
 * list and behave differently everywhere else. */
fun normalizeTags(raw: String): String =
    raw.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        .distinct().joinToString(", ")
