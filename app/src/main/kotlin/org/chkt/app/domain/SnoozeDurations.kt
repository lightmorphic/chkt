package org.chkt.app.domain

/** The six snooze lengths offered on a fired alert, user-customizable in
 * Settings. Shared between the Settings editor and the alert screen so
 * both always agree on labels. */
object SnoozeDurations {
    val DEFAULT = listOf(10, 30, 60, 180, 720, 1440)

    fun parse(raw: String?): List<Int> {
        val values = raw?.split(",")?.mapNotNull { it.trim().toIntOrNull()?.takeIf { m -> m > 0 } }
        return if (values != null && values.size == DEFAULT.size) values else DEFAULT
    }

    fun encode(minutes: List<Int>): String = minutes.joinToString(",")

    /** "10 min", "1 hr", "3 hrs", "1 day", "2 days" — always the coarsest
     * unit that divides evenly, same convention as the repeat picker. */
    fun format(minutes: Int): String = when {
        minutes % 1440 == 0 -> (minutes / 1440).let { d -> "$d " + if (d == 1) "day" else "days" }
        minutes % 60 == 0 -> (minutes / 60).let { h -> "$h " + if (h == 1) "hr" else "hrs" }
        else -> "$minutes min"
    }

    /** (amount, unit code "m"/"h"/"d") for editing — the same coarsest-unit
     * choice as format(), split into parts an amount+unit picker can use. */
    fun decompose(minutes: Int): Pair<Int, String> = when {
        minutes % 1440 == 0 -> minutes / 1440 to "d"
        minutes % 60 == 0 -> minutes / 60 to "h"
        else -> minutes to "m"
    }

    fun compose(amount: Int, unit: String): Int = when (unit) {
        "h" -> amount * 60
        "d" -> amount * 1440
        else -> amount
    }
}
