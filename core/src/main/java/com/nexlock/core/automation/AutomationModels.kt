package com.nexlock.core.automation

import com.nexlock.common.policy.AutomationDecision
import com.nexlock.common.policy.ScheduleWindow

data class LockSchedule(
    val id: Long,
    val label: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val enabled: Boolean,
) {
    fun toWindow(): ScheduleWindow = ScheduleWindow(id, label, startMinute, endMinute, daysMask, enabled)
}

data class TrustedNetwork(
    val ssid: String,
    val enabled: Boolean,
    val addedAt: Long,
)

data class LocationRule(
    val id: Long,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val enabled: Boolean,
)

/** Result of evaluating every automation rule for "right now". */
data class AutomationSnapshot(
    val decision: AutomationDecision = AutomationDecision.None,
    val scheduleEnabled: Boolean = false,
    val activeWindowLabel: String? = null,
    val connectedSsid: String? = null,
    val trustedNetworkMatched: Boolean = false,
    val trustedLocationLabel: String? = null,
    val temporaryUnlockActive: Boolean = false,
    val locationPermissionGranted: Boolean = false,
) {
    val protectionRelaxed: Boolean get() = decision is AutomationDecision.ForceUnlock
    val forcedBySchedule: Boolean get() = decision is AutomationDecision.ForceLock
}
