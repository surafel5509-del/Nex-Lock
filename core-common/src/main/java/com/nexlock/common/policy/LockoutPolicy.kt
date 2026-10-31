package com.nexlock.common.policy

/**
 * Progressive lockout schedule for failed authentication attempts.
 *
 * The first [FREE_ATTEMPTS] failures only warn the user; afterwards every attempt imposes
 * an exponentially growing cool-down. The policy is deliberately pure so it can be unit tested
 * and shared by the lock screen, settings screen and instrumented tests.
 */
object LockoutPolicy {

    /** Failed attempts allowed before the first cool-down. */
    const val FREE_ATTEMPTS: Int = 5

    /** Failures after which an intruder incident is considered for capture. */
    const val INTRUDER_TRIGGER_ATTEMPTS: Int = 3

    private val delaysMillis = mapOf(
        5 to 30_000L,
        6 to 60_000L,
        7 to 300_000L,
        8 to 900_000L,
    )

    private const val MAX_DELAY_MILLIS = 3_600_000L

    /** Cool-down length applied once [attempts] failed attempts have accumulated. */
    fun delayMillisFor(attempts: Int): Long {
        if (attempts < FREE_ATTEMPTS) return 0L
        return delaysMillis[attempts] ?: MAX_DELAY_MILLIS
    }

    fun requiresLockout(attempts: Int): Boolean = delayMillisFor(attempts) > 0L

    /**
     * Timestamp until which authentication must be refused.
     * Returns 0 when no lockout is active.
     */
    fun lockoutUntil(nowMillis: Long, attempts: Int): Long {
        val delay = delayMillisFor(attempts)
        return if (delay > 0L) nowMillis + delay else 0L
    }

    fun remainingMillis(nowMillis: Long, lockoutUntilMillis: Long): Long =
        (lockoutUntilMillis - nowMillis).coerceAtLeast(0L)
}
