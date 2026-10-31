package com.nexlock.common.policy

import java.time.LocalDateTime
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cheap guard against accidental algorithmic regressions in the hot path that runs on every
 * foreground app change. Budgets are intentionally generous so the test is not flaky on CI.
 */
class PolicyPerformanceTest {

    @Test
    fun `automation evaluation stays in the microsecond range`() {
        val context = AutomationContext(insideLockSchedule = true)
        repeat(1_000) { AutomationEvaluator.evaluate(context) }
        val elapsed = measureTimeMillis {
            repeat(50_000) { AutomationEvaluator.evaluate(context) }
        }
        assertTrue("50k evaluations took ${elapsed}ms", elapsed < 1_000)
    }

    @Test
    fun `schedule checks handle a hundred windows quickly`() {
        val windows = (0 until 100).map {
            ScheduleWindow(it.toLong(), "w$it", 8 * 60, 18 * 60, ScheduleCalculator.ALL_DAYS_MASK, true)
        }
        val now = LocalDateTime.of(2026, 3, 2, 12, 0)
        val elapsed = measureTimeMillis {
            repeat(2_000) { ScheduleCalculator.activeWindow(windows, now) }
        }
        assertTrue("schedule checks took ${elapsed}ms", elapsed < 2_000)
    }
}
