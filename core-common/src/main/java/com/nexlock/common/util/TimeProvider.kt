package com.nexlock.common.util

/** Indirection over the system clock so lockout logic is deterministic in tests. */
interface TimeProvider {
    fun nowMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
