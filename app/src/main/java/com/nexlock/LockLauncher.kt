package com.nexlock

import android.content.Context
import com.nexlock.common.util.NexLog
import com.nexlock.core.apps.LockDecision

/** Centralised, throttled launch of the lock screen from background components. */
object LockLauncher {

    @Volatile
    private var lastPackage: String? = null

    @Volatile
    private var lastLaunchedAt: Long = 0L

    fun show(context: Context, decision: LockDecision.Lock) {
        val now = System.currentTimeMillis()
        if (decision.packageName == lastPackage && now - lastLaunchedAt < THROTTLE_MILLIS) return
        lastPackage = decision.packageName
        lastLaunchedAt = now
        runCatching {
            context.startActivity(LockActivity.intent(context, decision.packageName, decision.appName))
        }.onFailure {
            NexLog.w(TAG, "Lock screen could not be launched (background activity start blocked)", it)
        }
    }

    private const val TAG = "LockLauncher"
    private const val THROTTLE_MILLIS = 700L
}
