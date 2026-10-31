package com.nexlock.common.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutPolicyTest {

    @Test
    fun `no lockout before the free attempt budget is exhausted`() {
        for (attempts in 0 until LockoutPolicy.FREE_ATTEMPTS) {
            assertEquals(0L, LockoutPolicy.delayMillisFor(attempts))
            assertFalse(LockoutPolicy.requiresLockout(attempts))
        }
    }

    @Test
    fun `delays grow progressively and are capped`() {
        val delays = (5..12).map { LockoutPolicy.delayMillisFor(it) }
        assertEquals(delays.sorted(), delays)
        assertEquals(30_000L, delays.first())
        assertEquals(3_600_000L, delays.last())
        assertTrue(delays.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `lockoutUntil returns zero until an attempt threshold is hit`() {
        val now = 1_000L
        assertEquals(0L, LockoutPolicy.lockoutUntil(now, 4))
        assertEquals(now + 30_000L, LockoutPolicy.lockoutUntil(now, 5))
    }

    @Test
    fun `remaining time never goes negative`() {
        assertEquals(0L, LockoutPolicy.remainingMillis(nowMillis = 5_000, lockoutUntilMillis = 1_000))
        assertEquals(4_000L, LockoutPolicy.remainingMillis(nowMillis = 1_000, lockoutUntilMillis = 5_000))
    }
}
