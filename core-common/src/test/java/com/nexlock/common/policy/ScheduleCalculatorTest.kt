package com.nexlock.common.policy

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCalculatorTest {

    private fun window(start: Int, end: Int, mask: Int = ScheduleCalculator.ALL_DAYS_MASK) =
        ScheduleWindow(id = 1, label = "Work", startMinute = start, endMinute = end, daysMask = mask, enabled = true)

    @Test
    fun `daytime window is active inside its bounds`() {
        val schedule = window(start = 9 * 60, end = 17 * 60)
        assertTrue(ScheduleCalculator.isActive(schedule, LocalDateTime.of(2026, 3, 2, 10, 0)))
        assertFalse(ScheduleCalculator.isActive(schedule, LocalDateTime.of(2026, 3, 2, 8, 59)))
        assertFalse(ScheduleCalculator.isActive(schedule, LocalDateTime.of(2026, 3, 2, 17, 0)))
    }

    @Test
    fun `overnight window spans midnight and respects the start day`() {
        // Monday only, 22:00 -> 07:00
        val mondayBit = 1 shl 0
        val schedule = window(start = 22 * 60, end = 7 * 60, mask = mondayBit)
        assertTrue(ScheduleCalculator.isActive(schedule, LocalDateTime.of(2026, 3, 2, 23, 0)))
        assertTrue(ScheduleCalculator.isActive(schedule, LocalDateTime.of(2026, 3, 3, 6, 30)))
        assertFalse(ScheduleCalculator.isActive(schedule, LocalDateTime.of(2026, 3, 3, 8, 0)))
        assertFalse(ScheduleCalculator.isActive(schedule, LocalDateTime.of(2026, 3, 2, 12, 0)))
    }

    @Test
    fun `disabled and zero-length windows never match`() {
        val disabled = window(0, 1440).copy(enabled = false)
        assertFalse(ScheduleCalculator.isActive(disabled, LocalDateTime.of(2026, 3, 2, 12, 0)))
        assertFalse(ScheduleCalculator.isActive(window(600, 600), LocalDateTime.of(2026, 3, 2, 10, 0)))
    }

    @Test
    fun `window search returns the first active schedule`() {
        val schedules = listOf(
            window(1, 2),
            window(9 * 60, 17 * 60),
        )
        val active = ScheduleCalculator.activeWindow(schedules, LocalDateTime.of(2026, 3, 2, 10, 0))
        assertEquals(schedules[1], active)
    }

    @Test
    fun `minute formatting is stable`() {
        assertEquals("08:05", ScheduleCalculator.formatMinuteOfDay(485))
        assertEquals("00:00", ScheduleCalculator.formatMinuteOfDay(1440))
    }
}
