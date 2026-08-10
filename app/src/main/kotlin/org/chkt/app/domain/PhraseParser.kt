package org.chkt.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Parses the structured voice phrasing used by the record widget. This is
 * deliberately NOT natural-language understanding, it accepts a small set of
 * predictable shapes and nothing else:
 *
 *   "remind me at 2pm to feed the cat"
 *   "remind me at 14:30 to call Sam"
 *   "remind me in 20 minutes to check the oven"
 *   "remind me tomorrow at 9 to book the dentist"
 *   "remind me on friday at 5pm to take the bins out"
 *   "remind me every day at 7am to take my tablets"
 */
object PhraseParser {

    data class Parsed(
        val title: String,
        val dueAt: ZonedDateTime,
        val repeat: RepeatRule = RepeatRule.None,
    )

    fun parse(rawInput: String, now: ZonedDateTime): Parsed? {
        var raw = rawInput.trim().lowercase()
            .removePrefix("remind me").trim()
        if (raw.isBlank()) return null

        var repeat: RepeatRule = RepeatRule.None
        var date: LocalDate? = null
        var time: LocalTime? = null

        // "every day/week ..." → repeat
        Regex("^every (day|morning|evening|week)\\b").find(raw)?.let { m ->
            repeat = when (m.groupValues[1]) {
                "week" -> RepeatRule.Weekly(setOf(now.dayOfWeek))
                else -> RepeatRule.Daily
            }
            raw = raw.removeRange(m.range).trim()
        }

        // "tomorrow"
        Regex("^tomorrow\\b").find(raw)?.let { m ->
            date = now.toLocalDate().plusDays(1)
            raw = raw.removeRange(m.range).trim()
        }

        // "on monday" (next occurrence of that weekday)
        Regex("^on (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b").find(raw)?.let { m ->
            val day = DayOfWeek.valueOf(m.groupValues[1].uppercase())
            date = now.toLocalDate().with(TemporalAdjusters.next(day))
            raw = raw.removeRange(m.range).trim()
        }

        // "in 20 minutes / 2 hours / 3 days"
        Regex("^in (\\d+) (minute|minutes|hour|hours|day|days)\\b").find(raw)?.let { m ->
            val n = m.groupValues[1].toLong()
            val due = when {
                m.groupValues[2].startsWith("minute") -> now.plusMinutes(n)
                m.groupValues[2].startsWith("hour") -> now.plusHours(n)
                else -> now.plusDays(n)
            }
            raw = raw.removeRange(m.range).trim()
            val title = extractTitle(raw) ?: return null
            return Parsed(title, due.withSecond(0).withNano(0), repeat)
        }

        // "at 2pm / at 14:30 / at 9", may appear after tomorrow/on-day too
        Regex("^at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b").find(raw)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifBlank { "0" }.toInt()
            when (m.groupValues[3]) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
            }
            if (hour !in 0..23 || minute !in 0..59) return null
            time = LocalTime.of(hour, minute)
            raw = raw.removeRange(m.range).trim()
        }

        // Some phrasings put the day after the time: "at 5pm on friday to ..."
        Regex("^on (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b").find(raw)?.let { m ->
            val day = DayOfWeek.valueOf(m.groupValues[1].uppercase())
            date = now.toLocalDate().with(TemporalAdjusters.next(day))
            raw = raw.removeRange(m.range).trim()
        }
        Regex("^tomorrow\\b").find(raw)?.let { m ->
            date = now.toLocalDate().plusDays(1)
            raw = raw.removeRange(m.range).trim()
        }

        val title = extractTitle(raw) ?: return null
        val t = time ?: return null

        var due = (date ?: now.toLocalDate()).atTime(t).atZone(now.zone)
        if (!due.isAfter(now)) {
            // "at 8am" said in the afternoon means tomorrow morning.
            if (date == null) due = due.plusDays(1) else return null
        }
        return Parsed(title, due, repeat)
    }

    private fun extractTitle(raw: String): String? {
        val t = raw.removePrefix("to ").trim().trimEnd('.')
        return t.ifBlank { null }
    }
}
