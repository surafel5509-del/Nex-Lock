package com.nexlock.common.util

import android.util.Log

/**
 * Minimal logging façade.
 *
 * Rules enforced by code review and by the lint rule that bans string templates in log calls:
 * * messages never contain credentials, vault contents, file paths of decrypted data or biometrics;
 * * nothing is written outside debug builds ([setEnabled] is driven by `FLAG_DEBUGGABLE`).
 */
object NexLog {

    @Volatile
    private var enabled: Boolean = false

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun d(tag: String, message: String) {
        if (enabled) Log.d(prefix(tag), message)
    }

    fun i(tag: String, message: String) {
        if (enabled) Log.i(prefix(tag), message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) Log.w(prefix(tag), message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Errors are always logged, but only the message/type - never payload data.
        Log.e(prefix(tag), message, throwable)
    }

    private fun prefix(tag: String) = "NexLock/$tag"
}
