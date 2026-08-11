package org.chkt.app

import org.chkt.app.domain.PhraseParser
import org.chkt.app.domain.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PhraseParserTest {
    // Monday 10 Aug 2026, 10:00
    private val now = ZonedDateTime.of(2026, 8, 10, 10, 0, 0, 0, ZoneId.of("Europe/London"))

    @Test
    fun `at time pm`() {
        val p = PhraseParser.parse("remind me at 2pm to feed the cat", now)!!
        assertEquals("feed the cat", p.title)
        assertEquals(now.withHour(14).withMinute(0), p.dueAt)
    }

    @Test
    fun `24 hour time`() {
        val p = PhraseParser.parse("remind me at 14:30 to call Sam", now)!!
        assertEquals("call sam", p.title)
        assertEquals(now.withHour(14).withMinute(30), p.dueAt)
    }

    @Test
    fun `past time rolls to tomorrow`() {
        val p = PhraseParser.parse("remind me at 8am to take my tablets", now)!!
        assertEquals(now.plusDays(1).withHour(8).withMinute(0), p.dueAt)
    }

    @Test
    fun `in minutes`() {
        val p = PhraseParser.parse("remind me in 20 minutes to check the oven", now)!!
        assertEquals("check the oven", p.title)
        assertEquals(now.plusMinutes(20), p.dueAt)
    }

    @Test
    fun `tomorrow at time`() {
        val p = PhraseParser.parse("remind me tomorrow at 9 to book the dentist", now)!!
        assertEquals(now.plusDays(1).withHour(9).withMinute(0), p.dueAt)
    }

    @Test
    fun `on weekday at time`() {
        val p = PhraseParser.parse("remind me on friday at 5pm to take the bins out", now)!!
        assertEquals(now.withDayOfMonth(14).withHour(17).withMinute(0), p.dueAt)
    }

    @Test
    fun `time before weekday`() {
        val p = PhraseParser.parse("remind me at 5pm on friday to take the bins out", now)!!
        assertEquals(now.withDayOfMonth(14).withHour(17).withMinute(0), p.dueAt)
    }

    @Test
    fun `every day repeats daily`() {
        val p = PhraseParser.parse("remind me every day at 7am to take my tablets", now)!!
        assertEquals(RepeatRule.Daily, p.repeat)
        assertEquals(now.plusDays(1).withHour(7).withMinute(0), p.dueAt)
    }

    @Test
    fun `bare hour means the next one of those, morning case`() {
        // Said at 9am, "at 10" means 10am today, an hour later.
        val morning = now.withHour(9)
        val p = PhraseParser.parse("remind me at 10 to put the cat out", morning)!!
        assertEquals(morning.withHour(10).withMinute(0), p.dueAt)
    }

    @Test
    fun `bare hour means the next one of those, evening rollover`() {
        // Said at 11pm, "at 10" means 10am tomorrow.
        val late = now.withHour(23)
        val p = PhraseParser.parse("remind me at 10 o'clock to put the cat out", late)!!
        assertEquals(late.plusDays(1).withHour(10).withMinute(0), p.dueAt)
    }

    @Test
    fun `bare hour afternoon picks the evening`() {
        // Said at 2pm, "at 10" means 10pm tonight.
        val afternoon = now.withHour(14)
        val p = PhraseParser.parse("remind me at 10 to lock up", afternoon)!!
        assertEquals(afternoon.withHour(22).withMinute(0), p.dueAt)
    }

    @Test
    fun `explicit am pm is never reinterpreted`() {
        val p = PhraseParser.parse("remind me at 10pm to lock up", now)!!
        assertEquals(now.withHour(22).withMinute(0), p.dueAt)
    }

    @Test
    fun `no time means no parse`() {
        assertNull(PhraseParser.parse("remind me to feed the cat", now))
        assertNull(PhraseParser.parse("feed the cat", now))
        assertNull(PhraseParser.parse("", now))
    }

    @Test
    fun `no task means no parse`() {
        assertNull(PhraseParser.parse("remind me at 2pm", now))
    }
}
