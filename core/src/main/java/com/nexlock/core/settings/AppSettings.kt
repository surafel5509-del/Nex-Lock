package com.nexlock.core.settings

import com.nexlock.common.model.AppLanguage
import com.nexlock.common.model.AuthMode
import com.nexlock.common.model.ThemeMode

/**
 * Complete, non-secret application configuration.
 *
 * Credential hashes are deliberately **not** part of this model: they are read through dedicated
 * accessors so they can never be accidentally rendered or logged by the UI layer.
 */
data class AppSettings(
    val protectionEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val authMode: AuthMode = AuthMode.PIN,
    val credentialConfigured: Boolean = false,
    val recoveryConfigured: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 0,
    val relockOnScreenOff: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val screenshotsBlocked: Boolean = true,
    val secureClipboardEnabled: Boolean = true,
    val intruderDetectionEnabled: Boolean = false,
    val intruderPhotoEnabled: Boolean = false,
    val intruderFailureThreshold: Int = 3,
    val notificationsEnabled: Boolean = true,
    val notifyIncidents: Boolean = true,
    val notifyFailedAuth: Boolean = true,
    val notifyPermissionIssues: Boolean = true,
    val notifyProtectionState: Boolean = true,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L,
    val lastCredentialChangeMillis: Long = 0L,
    val temporaryUnlockMinutes: Int = 5,
    val temporaryUnlockUntilMillis: Long = 0L,
    val automationEnabled: Boolean = true,
    val scheduleEnabled: Boolean = false,
    val wifiAutomationEnabled: Boolean = false,
    val locationAutomationEnabled: Boolean = false,
    val securityLogRetentionDays: Int = 30,
    val showRiskWarnings: Boolean = true,
) {
    val temporarilyUnlocked: Boolean
        get() = temporaryUnlockUntilMillis > System.currentTimeMillis()

    val lockTimeoutLabel: String
        get() = when (lockTimeoutSeconds) {
            0 -> "Immediately"
            30 -> "30 seconds"
            60 -> "1 minute"
            300 -> "5 minutes"
            900 -> "15 minutes"
            3600 -> "1 hour"
            else -> "$lockTimeoutSeconds s"
        }
}
