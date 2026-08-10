package org.chkt.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.MonthDay
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Repeat rules are stored as short strings so they survive export/import and
 * sync as plain data:
 *
 *  - ""                    one-off, no repeat
 *  - "DAILY"               every day at the same time
 *  - "WEEKLY:MON,THU"      chosen weekdays
 *  - "MONTHLY:15"          day of month (31 clamps to shorter months)
 *  - "MONTHLY:LAST"        last day of the month
 *  - "YEARLY:08-10"        every year on month-day
 *  - "EVERY:90m|12h|3d|2w" custom fixed interval
 */
sealed class RepeatRule {
    object None : RepeatRule()
    object Daily : RepeatRule()
    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule()
    data class Monthly(val dayOfMonth: Int, val last: Boolean = false) : RepeatRule()
    data class Yearly(val monthDay: MonthDay) : RepeatRule()
    data class Every(val interval: Duration) : RepeatRule()

    fun encode(): String = when (this) {
        None -> ""
        Daily -> "DAILY"
        is Weekly -> "WEEKLY:" + days.sortedBy { it.value }.joinToString(",") { it.name.take(3) }
        is Monthly -> if (last) "MONTHLY:LAST" else "MONTHLY:$dayOfMonth"
        is Yearly -> "YEARLY:%02d-%02d".format(monthDay.monthValue, monthDay.dayOfMonth)
        is Every -> "EVERY:" + encodeDuration(interval)
    }

    /**
     * The next occurrence strictly after [after], keeping the time-of-day of
     * [previous] (the occurrence that just fired or the first scheduled time).
     */
    fun nextAfter(previous: ZonedDateTime, after: ZonedDateTime): ZonedDateTime? {
        val time = previous.toLocalTime()
        return when (this) {
            None -> null
            Daily -> {
                var next = after.toLocalDate().atTime(time).atZone(after.zone)
                if (!next.isAfter(after)) next = next.plusDays(1)
                next
            }
            is Weekly -> {
                if (days.isEmpty()) return null
                var candidate = after.toLocalDate().atTime(time).atZone(after.zone)
                if (!candidate.isAfter(after)) candidate = candidate.plusDays(1)
                while (candidate.dayOfWeek !in days) candidate = candidate.plusDays(1)
                candidate
            }
            is Monthly -> {
                var monthStart = after.toLocalDate().withDayOfMonth(1)
                repeat(13) {
                    val day = if (last) monthStart.lengthOfMonth()
                    else dayOfMonth.coerceAtMost(monthStart.lengthOfMonth())
                    val candidate = monthStart.withDayOfMonth(day).atTime(time).atZone(after.zone)
                    if (candidate.isAfter(after)) return candidate
                    monthStart = monthStart.plusMonths(1)
                }
                null
            }
            is Yearly -> {
                var year = after.year
                repeat(2) {
                    val date = monthDay.atYear(year) // handles 29 Feb → 28 Feb off-leap-years
                    val candidate = date.atTime(time).atZone(after.zone)
                    if (candidate.isAfter(after)) return candidate
                    year += 1
                }
                null
            }
            is Every -> {
                if (interval.isZero || interval.isNegative) return null
                var next = previous
                // Step from the previous occurrence so the cadence never drifts,
                // but always land strictly after `after` (covers missed fires).
                while (!next.isAfter(after)) next = next.plus(interval)
                next
            }
        }
    }

    companion object {
        fun decode(raw: String): RepeatRule {
            if (raw.isBlank()) return None
            val parts = raw.split(":", limit = 2)
            return when (parts[0]) {
                "DAILY" -> Daily
                "WEEKLY" -> {
                    val days = parts.getOrElse(1) { "" }.split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.length >= 3 }
                        .mapNotNull { token ->
                            DayOfWeek.entries.firstOrNull { it.name.startsWith(token) }
                        }.toSet()
                    if (days.isEmpty()) None else Weekly(days)
                }
                "MONTHLY" -> {
                    val arg = parts.getOrElse(1) { "" }.trim()
                    if (arg == "LAST") Monthly(31, last = true)
                    else arg.toIntOrNull()?.takeIf { it in 1..31 }?.let { Monthly(it) } ?: None
                }
                "YEARLY" -> {
                    val arg = parts.getOrElse(1) { "" }.trim()
                    val md = arg.split("-")
                    val month = md.getOrNull(0)?.toIntOrNull()
                    val day = md.getOrNull(1)?.toIntOrNull()
                    if (month in 1..12 && day != null && day in 1..31) {
                        runCatching { Yearly(MonthDay.of(month!!, day)) }.getOrDefault(None)
                    } else None
                }
                "EVERY" -> decodeDuration(parts.getOrElse(1) { "" })?.let { Every(it) } ?: None
                else -> None
            }
        }

        private fun encodeDuration(d: Duration): String {
            val minutes = d.toMinutes()
            return when {
                minutes % (7 * 24 * 60) == 0L -> "${minutes / (7 * 24 * 60)}w"
                minutes % (24 * 60) == 0L -> "${minutes / (24 * 60)}d"
                minutes % 60 == 0L -> "${minutes / 60}h"
                else -> "${minutes}m"
            }
        }

        private fun decodeDuration(raw: String): Duration? {
            val match = Regex("^(\\d+)([mhdw])$").find(raw.trim()) ?: return null
            val n = match.groupValues[1].toLongOrNull() ?: return null
            if (n <= 0) return null
            return when (match.groupValues[2]) {
                "m" -> Duration.ofMinutes(n)
                "h" -> Duration.ofHours(n)
                "d" -> Duration.ofDays(n)
                "w" -> Duration.ofDays(7 * n)
                else -> null
            }
        }
    }
}
