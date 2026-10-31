package com.nexlock.core.events

import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity

/** Domain view of a locally stored security event. */
data class SecurityEvent(
    val id: Long,
    val type: SecurityEventType,
    val severity: SecuritySeverity,
    val message: String,
    val packageName: String?,
    val timestamp: Long,
)
