package com.nexlock.common.util

object NexConstants {

    /** DataStore file that holds non-secret preferences. */
    const val SETTINGS_DATASTORE = "nexlock_settings"

    /** Keystore aliases - never change without a migration, they own existing ciphertext. */
    const val KEYSTORE_MASTER_ALIAS = "nexlock_master_aes_key"
    const val KEYSTORE_PEPPER_ALIAS = "nexlock_credential_pepper"

    const val PBKDF2_ITERATIONS = 210_000
    const val PBKDF2_KEY_BITS = 256

    /** Encrypted media lives here, inside the app sandbox. */
    const val VAULT_DIR = "vault"
    const val INTRUDER_DIR = "intruder"
    const val TEMP_DIR = "secure_tmp"

    /** Notification channels. */
    const val CHANNEL_SECURITY = "nexlock_security"
    const val CHANNEL_STATUS = "nexlock_status"

    const val NOTIFICATION_STATUS_ID = 1001
    const val NOTIFICATION_SECURITY_ID = 1002

    /** Foreground monitoring service notification. */
    const val MONITOR_NOTIFICATION_ID = 1100
    const val MONITOR_CHANNEL_ID = "nexlock_monitor"

    /** Delay between foreground polls when only usage-access is granted. */
    const val MONITOR_POLL_INTERVAL_MS = 900L

    /** User-facing maximum size for a single vault import (200 MB). */
    const val MAX_IMPORT_BYTES = 200L * 1024L * 1024L

    /** Largest file we will decrypt into memory for a thumbnail. */
    const val MAX_THUMBNAIL_BYTES = 24L * 1024L * 1024L

    const val LOCK_SCREEN_TIMEOUT_GRACE_MS = 150L
}
