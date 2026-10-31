package com.nexlock.security.backup

import kotlinx.serialization.Serializable

/** Portable, passphrase-encrypted backup schema. Media files are intentionally excluded. */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val createdAt: Long = 0L,
    val appVersion: String = "",
    val settings: Map<String, String> = emptyMap(),
    val lockedApps: List<LockedAppBackup> = emptyList(),
    val notes: List<NoteBackup> = emptyList(),
    val schedules: List<ScheduleBackup> = emptyList(),
    val trustedNetworks: List<String> = emptyList(),
    val locationRules: List<LocationRuleBackup> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

@Serializable
data class LockedAppBackup(
    val packageName: String,
    val appName: String = "",
    val category: String = "OTHER",
    val lockedAt: Long = 0L,
)

@Serializable
data class NoteBackup(
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ScheduleBackup(
    val label: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val enabled: Boolean,
)

@Serializable
data class LocationRuleBackup(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val enabled: Boolean,
)
