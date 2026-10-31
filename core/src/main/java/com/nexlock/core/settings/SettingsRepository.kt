package com.nexlock.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for preferences.
 *
 * Only non-secret values live in DataStore; credential hashes are read through
 * [credentialHash]/[recoveryHash] which are intentionally excluded from [AppSettings] and from
 * backups (the Keystore pepper makes them device-bound anyway).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toAppSettings() }

    suspend fun current(): AppSettings = settings.first()

    // ---------------------------------------------------------------- credentials

    suspend fun credentialHash(): String? = dataStore.data.first()[KEY_CREDENTIAL_HASH]

    suspend fun recoveryHash(): String? = dataStore.data.first()[KEY_RECOVERY_HASH]

    suspend fun saveCredential(mode: com.nexlock.common.model.AuthMode, hash: String) {
        dataStore.edit {
            it[KEY_CREDENTIAL_HASH] = hash
            it[KEY_AUTH_MODE] = mode.name
            it[KEY_CREDENTIAL_CONFIGURED] = true
            it[KEY_LAST_CREDENTIAL_CHANGE] = System.currentTimeMillis()
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun saveRecoveryHash(hash: String) {
        dataStore.edit {
            it[KEY_RECOVERY_HASH] = hash
            it[KEY_RECOVERY_CONFIGURED] = true
        }
    }

    suspend fun clearCredential() {
        dataStore.edit {
            it.remove(KEY_CREDENTIAL_HASH)
            it[KEY_CREDENTIAL_CONFIGURED] = false
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun clearRecovery() {
        dataStore.edit {
            it.remove(KEY_RECOVERY_HASH)
            it[KEY_RECOVERY_CONFIGURED] = false
        }
    }

    suspend fun recordFailedAttempt(attempts: Int, lockoutUntilMillis: Long) {
        dataStore.edit {
            it[KEY_FAILED_ATTEMPTS] = attempts
            it[KEY_LOCKOUT_UNTIL] = lockoutUntilMillis
        }
    }

    suspend fun resetFailedAttempts() {
        dataStore.edit {
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    // ---------------------------------------------------------------- boolean flags

    suspend fun setProtectionEnabled(value: Boolean) = edit { it[KEY_PROTECTION_ENABLED] = value }
    suspend fun setOnboardingCompleted(value: Boolean) = edit { it[KEY_ONBOARDING] = value }
    suspend fun setBiometricEnabled(value: Boolean) = edit { it[KEY_BIOMETRIC] = value }
    suspend fun setRelockOnScreenOff(value: Boolean) = edit { it[KEY_RELOCK_SCREEN_OFF] = value }
    suspend fun setDynamicColor(value: Boolean) = edit { it[KEY_DYNAMIC_COLOR] = value }
    suspend fun setHapticsEnabled(value: Boolean) = edit { it[KEY_HAPTICS] = value }
    suspend fun setScreenshotsBlocked(value: Boolean) = edit { it[KEY_SCREENSHOTS] = value }
    suspend fun setSecureClipboardEnabled(value: Boolean) = edit { it[KEY_SECURE_CLIPBOARD] = value }
    suspend fun setIntruderDetectionEnabled(value: Boolean) = edit { it[KEY_INTRUDER] = value }
    suspend fun setIntruderPhotoEnabled(value: Boolean) = edit { it[KEY_INTRUDER_PHOTO] = value }
    suspend fun setNotificationsEnabled(value: Boolean) = edit { it[KEY_NOTIFICATIONS] = value }
    suspend fun setNotifyIncidents(value: Boolean) = edit { it[KEY_NOTIFY_INCIDENTS] = value }
    suspend fun setNotifyFailedAuth(value: Boolean) = edit { it[KEY_NOTIFY_FAILED_AUTH] = value }
    suspend fun setNotifyPermissionIssues(value: Boolean) = edit { it[KEY_NOTIFY_PERMISSIONS] = value }
    suspend fun setNotifyProtectionState(value: Boolean) = edit { it[KEY_NOTIFY_PROTECTION] = value }
    suspend fun setAutomationEnabled(value: Boolean) = edit { it[KEY_AUTOMATION] = value }
    suspend fun setScheduleEnabled(value: Boolean) = edit { it[KEY_SCHEDULE_ENABLED] = value }
    suspend fun setWifiAutomationEnabled(value: Boolean) = edit { it[KEY_WIFI_AUTOMATION] = value }
    suspend fun setLocationAutomationEnabled(value: Boolean) = edit { it[KEY_LOCATION_AUTOMATION] = value }
    suspend fun setShowRiskWarnings(value: Boolean) = edit { it[KEY_SHOW_RISK] = value }

    // ---------------------------------------------------------------- typed values

    suspend fun setAuthMode(mode: com.nexlock.common.model.AuthMode) = edit { it[KEY_AUTH_MODE] = mode.name }
    suspend fun setThemeMode(mode: com.nexlock.common.model.ThemeMode) = edit { it[KEY_THEME] = mode.name }
    suspend fun setLanguage(language: com.nexlock.common.model.AppLanguage) = edit { it[KEY_LANGUAGE] = language.name }
    suspend fun setLockTimeoutSeconds(seconds: Int) = edit { it[KEY_LOCK_TIMEOUT] = seconds.coerceIn(0, 3600) }
    suspend fun setIntruderFailureThreshold(value: Int) = edit { it[KEY_INTRUDER_THRESHOLD] = value.coerceIn(2, 10) }
    suspend fun setTemporaryUnlockMinutes(value: Int) = edit { it[KEY_TEMP_UNLOCK_MINUTES] = value.coerceIn(1, 120) }
    suspend fun setTemporaryUnlockUntil(millis: Long) = edit { it[KEY_TEMP_UNLOCK_UNTIL] = millis }
    suspend fun setSecurityLogRetentionDays(days: Int) = edit { it[KEY_LOG_RETENTION] = days.coerceIn(1, 365) }

    // ---------------------------------------------------------------- backup support

    /** Non-secret, user-meaningful preferences that are safe to move between devices. */
    suspend fun exportPreferenceMap(): Map<String, String> {
        val prefs = dataStore.data.first()
        return EXPORTABLE_KEYS.mapNotNull { (backupKey, key) ->
            prefs[key]?.let { backupKey to it.toString() }
        }.toMap()
    }

    suspend fun importPreferenceMap(values: Map<String, String>) {
        dataStore.edit { prefs ->
            values.forEach { (backupKey, raw) ->
                when (backupKey) {
                    "theme" -> prefs[KEY_THEME] = raw
                    "language" -> prefs[KEY_LANGUAGE] = raw
                    "dynamicColor" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_DYNAMIC_COLOR] = it }
                    "haptics" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_HAPTICS] = it }
                    "lockTimeout" -> raw.toIntOrNull()?.let { prefs[KEY_LOCK_TIMEOUT] = it.coerceIn(0, 3600) }
                    "relockOnScreenOff" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_RELOCK_SCREEN_OFF] = it }
                    "screenshotsBlocked" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_SCREENSHOTS] = it }
                    "secureClipboard" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_SECURE_CLIPBOARD] = it }
                    "intruderDetection" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_INTRUDER] = it }
                    "intruderPhoto" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_INTRUDER_PHOTO] = it }
                    "intruderThreshold" -> raw.toIntOrNull()?.let { prefs[KEY_INTRUDER_THRESHOLD] = it.coerceIn(2, 10) }
                    "notifications" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_NOTIFICATIONS] = it }
                    "notifyIncidents" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_NOTIFY_INCIDENTS] = it }
                    "notifyFailedAuth" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_NOTIFY_FAILED_AUTH] = it }
                    "notifyPermissions" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_NOTIFY_PERMISSIONS] = it }
                    "notifyProtection" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_NOTIFY_PROTECTION] = it }
                    "automation" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_AUTOMATION] = it }
                    "scheduleEnabled" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_SCHEDULE_ENABLED] = it }
                    "wifiAutomation" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_WIFI_AUTOMATION] = it }
                    "locationAutomation" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_LOCATION_AUTOMATION] = it }
                    "temporaryUnlockMinutes" -> raw.toIntOrNull()?.let { prefs[KEY_TEMP_UNLOCK_MINUTES] = it.coerceIn(1, 120) }
                    "logRetentionDays" -> raw.toIntOrNull()?.let { prefs[KEY_LOG_RETENTION] = it.coerceIn(1, 365) }
                    "showRiskWarnings" -> raw.toBooleanStrictOrNull()?.let { prefs[KEY_SHOW_RISK] = it }
                }
            }
        }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) = dataStore.edit(block)

    companion object {
        val KEY_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val KEY_CREDENTIAL_CONFIGURED = booleanPreferencesKey("credential_configured")
        val KEY_RECOVERY_CONFIGURED = booleanPreferencesKey("recovery_configured")
        val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val KEY_RELOCK_SCREEN_OFF = booleanPreferencesKey("relock_on_screen_off")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_SCREENSHOTS = booleanPreferencesKey("screenshots_blocked")
        val KEY_SECURE_CLIPBOARD = booleanPreferencesKey("secure_clipboard")
        val KEY_INTRUDER = booleanPreferencesKey("intruder_detection")
        val KEY_INTRUDER_PHOTO = booleanPreferencesKey("intruder_photo")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_NOTIFY_INCIDENTS = booleanPreferencesKey("notify_incidents")
        val KEY_NOTIFY_FAILED_AUTH = booleanPreferencesKey("notify_failed_auth")
        val KEY_NOTIFY_PERMISSIONS = booleanPreferencesKey("notify_permissions")
        val KEY_NOTIFY_PROTECTION = booleanPreferencesKey("notify_protection")
        val KEY_AUTOMATION = booleanPreferencesKey("automation_enabled")
        val KEY_SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val KEY_WIFI_AUTOMATION = booleanPreferencesKey("wifi_automation")
        val KEY_LOCATION_AUTOMATION = booleanPreferencesKey("location_automation")
        val KEY_SHOW_RISK = booleanPreferencesKey("show_risk_warnings")

        val KEY_AUTH_MODE = stringPreferencesKey("auth_mode")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_CREDENTIAL_HASH = stringPreferencesKey("credential_hash")
        val KEY_RECOVERY_HASH = stringPreferencesKey("recovery_hash")

        val KEY_FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val KEY_LOCKOUT_UNTIL = longPreferencesKey("lockout_until")
        val KEY_LAST_CREDENTIAL_CHANGE = longPreferencesKey("last_credential_change")
        val KEY_LOCK_TIMEOUT = intPreferencesKey("lock_timeout_seconds")
        val KEY_INTRUDER_THRESHOLD = intPreferencesKey("intruder_threshold")
        val KEY_TEMP_UNLOCK_MINUTES = intPreferencesKey("temp_unlock_minutes")
        val KEY_TEMP_UNLOCK_UNTIL = longPreferencesKey("temp_unlock_until")
        val KEY_LOG_RETENTION = intPreferencesKey("log_retention_days")


        /** Backup key -> preference key. Credentials and counters are excluded on purpose. */
        private val EXPORTABLE_KEYS: Map<String, Preferences.Key<*>> = mapOf(
            "theme" to KEY_THEME,
            "language" to KEY_LANGUAGE,
            "dynamicColor" to KEY_DYNAMIC_COLOR,
            "haptics" to KEY_HAPTICS,
            "lockTimeout" to KEY_LOCK_TIMEOUT,
            "relockOnScreenOff" to KEY_RELOCK_SCREEN_OFF,
            "screenshotsBlocked" to KEY_SCREENSHOTS,
            "secureClipboard" to KEY_SECURE_CLIPBOARD,
            "intruderDetection" to KEY_INTRUDER,
            "intruderPhoto" to KEY_INTRUDER_PHOTO,
            "intruderThreshold" to KEY_INTRUDER_THRESHOLD,
            "notifications" to KEY_NOTIFICATIONS,
            "notifyIncidents" to KEY_NOTIFY_INCIDENTS,
            "notifyFailedAuth" to KEY_NOTIFY_FAILED_AUTH,
            "notifyPermissions" to KEY_NOTIFY_PERMISSIONS,
            "notifyProtection" to KEY_NOTIFY_PROTECTION,
            "automation" to KEY_AUTOMATION,
            "scheduleEnabled" to KEY_SCHEDULE_ENABLED,
            "wifiAutomation" to KEY_WIFI_AUTOMATION,
            "locationAutomation" to KEY_LOCATION_AUTOMATION,
            "temporaryUnlockMinutes" to KEY_TEMP_UNLOCK_MINUTES,
            "logRetentionDays" to KEY_LOG_RETENTION,
            "showRiskWarnings" to KEY_SHOW_RISK,
        )
    }
}

private fun Preferences.toAppSettings(): AppSettings = AppSettings(
    protectionEnabled = this[SettingsRepository.KEY_PROTECTION_ENABLED] ?: false,
    onboardingCompleted = this[SettingsRepository.KEY_ONBOARDING] ?: false,
    authMode = com.nexlock.common.model.AuthMode.fromStorage(this[SettingsRepository.KEY_AUTH_MODE]),
    credentialConfigured = this[SettingsRepository.KEY_CREDENTIAL_CONFIGURED] ?: false,
    recoveryConfigured = this[SettingsRepository.KEY_RECOVERY_CONFIGURED] ?: false,
    biometricEnabled = this[SettingsRepository.KEY_BIOMETRIC] ?: false,
    lockTimeoutSeconds = this[SettingsRepository.KEY_LOCK_TIMEOUT] ?: 0,
    relockOnScreenOff = this[SettingsRepository.KEY_RELOCK_SCREEN_OFF] ?: true,
    themeMode = com.nexlock.common.model.ThemeMode.fromStorage(this[SettingsRepository.KEY_THEME]),
    dynamicColor = this[SettingsRepository.KEY_DYNAMIC_COLOR] ?: true,
    hapticsEnabled = this[SettingsRepository.KEY_HAPTICS] ?: true,
    screenshotsBlocked = this[SettingsRepository.KEY_SCREENSHOTS] ?: true,
    secureClipboardEnabled = this[SettingsRepository.KEY_SECURE_CLIPBOARD] ?: true,
    intruderDetectionEnabled = this[SettingsRepository.KEY_INTRUDER] ?: false,
    intruderPhotoEnabled = this[SettingsRepository.KEY_INTRUDER_PHOTO] ?: false,
    intruderFailureThreshold = this[SettingsRepository.KEY_INTRUDER_THRESHOLD] ?: 3,
    notificationsEnabled = this[SettingsRepository.KEY_NOTIFICATIONS] ?: true,
    notifyIncidents = this[SettingsRepository.KEY_NOTIFY_INCIDENTS] ?: true,
    notifyFailedAuth = this[SettingsRepository.KEY_NOTIFY_FAILED_AUTH] ?: true,
    notifyPermissionIssues = this[SettingsRepository.KEY_NOTIFY_PERMISSIONS] ?: true,
    notifyProtectionState = this[SettingsRepository.KEY_NOTIFY_PROTECTION] ?: true,
    language = com.nexlock.common.model.AppLanguage.fromStorage(this[SettingsRepository.KEY_LANGUAGE]),
    failedAttempts = this[SettingsRepository.KEY_FAILED_ATTEMPTS] ?: 0,
    lockoutUntilMillis = this[SettingsRepository.KEY_LOCKOUT_UNTIL] ?: 0L,
    lastCredentialChangeMillis = this[SettingsRepository.KEY_LAST_CREDENTIAL_CHANGE] ?: 0L,
    temporaryUnlockMinutes = this[SettingsRepository.KEY_TEMP_UNLOCK_MINUTES] ?: 5,
    temporaryUnlockUntilMillis = this[SettingsRepository.KEY_TEMP_UNLOCK_UNTIL] ?: 0L,
    automationEnabled = this[SettingsRepository.KEY_AUTOMATION] ?: true,
    scheduleEnabled = this[SettingsRepository.KEY_SCHEDULE_ENABLED] ?: false,
    wifiAutomationEnabled = this[SettingsRepository.KEY_WIFI_AUTOMATION] ?: false,
    locationAutomationEnabled = this[SettingsRepository.KEY_LOCATION_AUTOMATION] ?: false,
    securityLogRetentionDays = this[SettingsRepository.KEY_LOG_RETENTION] ?: 30,
    showRiskWarnings = this[SettingsRepository.KEY_SHOW_RISK] ?: true,
)
