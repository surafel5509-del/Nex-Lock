package com.nexlock.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexlock.common.model.AppCategory
import com.nexlock.common.model.AuthMode
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.model.VaultKind

/**
 * Registry of applications the user chose to protect.
 * Only launcher applications are ever listed, and nothing is collected for advertising.
 */
@Entity(tableName = "locked_apps", indices = [Index("isLocked")])
data class LockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLocked: Boolean = true,
    val category: AppCategory = AppCategory.OTHER,
    val lockedAt: Long = System.currentTimeMillis(),
)

/** Local security journal. Never leaves the device. */
@Entity(tableName = "security_events", indices = [Index("timestamp"), Index("type")])
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SecurityEventType,
    val severity: SecuritySeverity,
    val message: String,
    val packageName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Encrypted vault media. [fileName] points at a file in the app's encrypted vault directory. */
@Entity(tableName = "vault_items", indices = [Index("kind"), Index("createdAt")])
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: VaultKind,
    val displayName: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Notes are stored with title and body already encrypted by the Keystore AES-GCM key. */
@Entity(tableName = "secure_notes")
data class SecureNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleCipher: String,
    val contentCipher: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Intruder incidents: timestamp + failed attempts + optional encrypted capture. */
@Entity(tableName = "intruder_incidents", indices = [Index("timestamp")])
data class IntruderIncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val failedAttempts: Int,
    val photoFileName: String? = null,
    val packageName: String? = null,
    val note: String? = null,
)

@Entity(tableName = "lock_schedules")
data class LockScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val enabled: Boolean = true,
)

@Entity(tableName = "trusted_wifi")
data class TrustedWifiEntity(
    @PrimaryKey val ssid: String,
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "location_rules")
data class LocationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val enabled: Boolean = true,
)

/** A named bundle of authentication preferences (currently: required mode + timeout). */
@Entity(tableName = "lock_profiles")
data class LockProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val authMode: AuthMode,
    val timeoutSeconds: Int,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
