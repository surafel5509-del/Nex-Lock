package com.nexlock.design.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity

/** Consistent iconography for the local security journal. */
fun securityEventIcon(type: SecurityEventType): ImageVector = when (type) {
    SecurityEventType.PROTECTION_ENABLED, SecurityEventType.PROTECTION_DISABLED -> Icons.Rounded.Security
    SecurityEventType.APP_LOCKED -> Icons.Rounded.Lock
    SecurityEventType.APP_UNLOCKED, SecurityEventType.TEMPORARY_UNLOCK -> Icons.Rounded.LockOpen
    SecurityEventType.AUTH_SUCCESS -> Icons.Rounded.CheckCircle
    SecurityEventType.AUTH_FAILURE -> Icons.Rounded.Warning
    SecurityEventType.LOCKOUT_TRIGGERED -> Icons.Rounded.HourglassEmpty
    SecurityEventType.INTRUDER_CAPTURED -> Icons.Rounded.CameraAlt
    SecurityEventType.CREDENTIAL_CHANGED -> Icons.Rounded.Password
    SecurityEventType.CREDENTIAL_RECOVERED -> Icons.Rounded.Restore
    SecurityEventType.BIOMETRIC_ENABLED, SecurityEventType.BIOMETRIC_DISABLED -> Icons.Rounded.Password
    SecurityEventType.VAULT_IMPORT, SecurityEventType.VAULT_EXPORT -> Icons.Rounded.Folder
    SecurityEventType.VAULT_DELETE -> Icons.Rounded.Folder
    SecurityEventType.AUTOMATION_APPLIED -> Icons.Rounded.Schedule
    SecurityEventType.BACKUP_CREATED, SecurityEventType.BACKUP_RESTORED -> Icons.Rounded.Backup
    SecurityEventType.RISK_DETECTED -> Icons.Rounded.Error
}

fun severityIcon(severity: SecuritySeverity): ImageVector = when (severity) {
    SecuritySeverity.INFO -> Icons.Rounded.CheckCircle
    SecuritySeverity.WARNING -> Icons.Rounded.Warning
    SecuritySeverity.CRITICAL -> Icons.Rounded.Error
}

/** Placeholder-free icon for empty app lists. */
val AppsIcon: ImageVector = Icons.Rounded.Apps
val TimerIcon: ImageVector = Icons.Rounded.Timer
