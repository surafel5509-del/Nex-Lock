package com.nexlock.core.apps

import android.content.Context
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.policy.AutomationDecision
import com.nexlock.common.util.NexLog
import com.nexlock.core.automation.AutomationRepository
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LockDecision {
    /** Nothing to do: not protected, already unlocked, or NexLock itself. */
    data object Ignore : LockDecision

    /** Show the lock screen for this package. */
    data class Lock(val packageName: String, val appName: String) : LockDecision
}

/**
 * Single decision point shared by the accessibility service and the polling monitor.
 *
 * Keeping the policy here means both triggers behave identically and the logic is unit-testable
 * without Android services.
 */
@Singleton
class LockEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lockedAppsRepository: LockedAppsRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionsManager: UnlockSessionManager,
    private val automationRepository: AutomationRepository,
    private val events: SecurityEventRepository,
) {

    @Volatile
    private var lastLoggedPackage: String? = null

    @Volatile
    private var lastLoggedAt: Long = 0L

    suspend fun onForegroundApp(packageName: String): LockDecision {
        if (packageName.isBlank()) return LockDecision.Ignore
        if (packageName == context.packageName) return LockDecision.Ignore
        if (packageName in SYSTEM_UI_PACKAGES) return LockDecision.Ignore

        sessionsManager.onForegroundChanged(packageName)

        val settings = settingsRepository.current()
        if (!settings.protectionEnabled) return LockDecision.Ignore

        // Without a configured credential the gate could never be opened; the UI prevents enabling
        // protection in that state, and this guard keeps a stale preference from bricking an app.
        if (!settings.credentialConfigured) return LockDecision.Ignore

        // Unknown apps are never listed as protected; this is a cheap in-memory check.
        if (!lockedAppsRepository.isLocked(packageName)) return LockDecision.Ignore

        if (sessionsManager.isUnlocked(packageName)) return LockDecision.Ignore

        val automation = automationRepository.evaluateNow()
        if (automation.decision is AutomationDecision.ForceUnlock) {
            NexLog.d(TAG, "Protection relaxed: ${(automation.decision as AutomationDecision.ForceUnlock).reason}")
            return LockDecision.Ignore
        }

        val name = lockedAppsRepository.appName(packageName) ?: packageName
        logLockShown(packageName, name)
        return LockDecision.Lock(packageName, name)
    }

    /** Called when the user successfully authenticates on the lock screen. */
    suspend fun onUnlockSucceeded(packageName: String) {
        sessionsManager.grantUnlock(packageName)
        lastLoggedPackage = null
        lastLoggedAt = 0L
    }

    suspend fun relockEverything() = sessionsManager.relockAll()

    private suspend fun logLockShown(packageName: String, appName: String) {
        val now = System.currentTimeMillis()
        // Throttle repeated entries for the same app within a minute to keep the journal readable.
        if (lastLoggedPackage == packageName && now - lastLoggedAt < LOG_THROTTLE_MILLIS) return
        lastLoggedPackage = packageName
        lastLoggedAt = now
        events.log(
            SecurityEventType.APP_LOCKED,
            SecuritySeverity.INFO,
            "Lock screen shown for $appName",
            packageName,
        )
    }

    companion object {
        private const val TAG = "LockEngine"
        private const val LOG_THROTTLE_MILLIS = 60_000L
        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.android.settings",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
    }
}
