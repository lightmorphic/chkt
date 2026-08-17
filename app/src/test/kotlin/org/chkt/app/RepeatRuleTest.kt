package org.chkt.app

import org.chkt.app.domain.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZonedDateTime

class RepeatRuleTest {
    private val zone = ZoneId.of("Europe/London")
    private fun zdt(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    @Test
    fun `encode and decode round-trip`() {
        val rules = listOf(
            RepeatRule.None,
            RepeatRule.Daily,
            RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            RepeatRule.Monthly(15),
            RepeatRule.Monthly(31, last = true),
            RepeatRule.Yearly(MonthDay.of(8, 10)),
            RepeatRule.Every(Duration.ofMinutes(90)),
            RepeatRule.Every(Duration.ofDays(3)),
            RepeatRule.Every(Duration.ofDays(14)),
            RepeatRule.EveryYears(3),
        )
        rules.forEach { rule ->
            assertEquals(rule, RepeatRule.decode(rule.encode()))
        }
    }

    @Test
    fun `none never repeats`() {
        assertNull(RepeatRule.None.nextAfter(zdt(2026, 8, 10, 9, 0), zdt(2026, 8, 10, 9, 1)))
    }

    @Test
    fun `daily keeps the time of day`() {
        val next = RepeatRule.Daily.nextAfter(zdt(2026, 8, 10, 9, 0), zdt(2026, 8, 10, 9, 0))
        assertEquals(zdt(2026, 8, 11, 9, 0), next)
    }

    @Test
    fun `weekly finds the next chosen weekday`() {
        // 10 Aug 2026 is a Monday.
        val rule = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val next = rule.nextAfter(zdt(2026, 8, 10, 9, 0), zdt(2026, 8, 10, 9, 0))
        assertEquals(zdt(2026, 8, 14, 9, 0), next) // Friday
        val after = rule.nextAfter(zdt(2026, 8, 10, 9, 0), next!!)
        assertEquals(zdt(2026, 8, 17, 9, 0), after) // next Monday
    }

    @Test
    fun `monthly day 31 clamps to short months`() {
        val rule = RepeatRule.Monthly(31)
        val next = rule.nextAfter(zdt(2026, 8, 31, 9, 0), zdt(2026, 8, 31, 9, 0))
        assertEquals(zdt(2026, 9, 30, 9, 0), next)
    }

    @Test
    fun `monthly last day lands on actual month ends`() {
        val rule = RepeatRule.Monthly(31, last = true)
        val next = rule.nextAfter(zdt(2026, 1, 31, 8, 0), zdt(2026, 1, 31, 8, 0))
        assertEquals(zdt(2026, 2, 28, 8, 0), next)
    }

    @Test
    fun `yearly handles 29 february gracefully`() {
        val rule = RepeatRule.Yearly(MonthDay.of(2, 29))
        val next = rule.nextAfter(zdt(2026, 2, 28, 10, 0), zdt(2026, 3, 1, 0, 0))
        // 2027 is not a leap year: MonthDay.atYear clamps to 28 Feb.
        assertEquals(zdt(2027, 2, 28, 10, 0), next)
    }

    @Test
    fun `every interval steps from the previous occurrence without drift`() {
        val rule = RepeatRule.Every(Duration.ofHours(6))
        val prev = zdt(2026, 8, 10, 6, 0)
        // Fired late (07:30), next should still be 12:00, not 13:30.
        val next = rule.nextAfter(prev, zdt(2026, 8, 10, 7, 30))
        assertEquals(zdt(2026, 8, 10, 12, 0), next)
    }

    @Test
    fun `every interval catches up over long gaps`() {
        val rule = RepeatRule.Every(Duration.ofDays(1))
        val prev = zdt(2026, 8, 1, 9, 0)
        val next = rule.nextAfter(prev, zdt(2026, 8, 10, 10, 0))
        assertEquals(zdt(2026, 8, 11, 9, 0), next)
    }

    @Test
    fun `every N years steps by calendar years, no drift`() {
        val rule = RepeatRule.EveryYears(3)
        val prev = zdt(2020, 8, 10, 9, 0)
        val next = rule.nextAfter(prev, zdt(2026, 8, 10, 9, 0))
        // Two 3-year steps from 2020 land on 2026, which isn't after itself,
        // so the third step (2029) is the first strictly-after occurrence.
        assertEquals(zdt(2029, 8, 10, 9, 0), next)
    }

    @Test
    fun `every N years handles 29 february gracefully`() {
        val rule = RepeatRule.EveryYears(3)
        val next = rule.nextAfter(zdt(2024, 2, 29, 10, 0), zdt(2024, 3, 1, 0, 0))
        // 2027 is not a leap year: plusYears clamps to 28 Feb.
        assertEquals(zdt(2027, 2, 28, 10, 0), next)
    }

    @Test
    fun `garbage decodes to none`() {
        listOf("WEEKLY:", "MONTHLY:99", "YEARLY:13-40", "EVERY:0d", "EVERY:0y", "EVERY:xyz", "BANANA")
            .forEach { assertEquals(RepeatRule.None, RepeatRule.decode(it)) }
    }

    @Test
    fun `daily next is always in the future`() {
        val next = RepeatRule.Daily.nextAfter(zdt(2026, 8, 10, 9, 0), zdt(2026, 8, 12, 23, 59))
        assertTrue(next!!.isAfter(zdt(2026, 8, 12, 23, 59)))
    }
}
