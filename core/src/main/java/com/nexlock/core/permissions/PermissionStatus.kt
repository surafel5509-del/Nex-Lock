package com.nexlock.core.permissions

/**
 * Snapshot of the permissions NexLock actually needs. Everything is optional: the app degrades
 * gracefully and tells the user exactly what is missing and why it matters.
 */
data class PermissionStatus(
    val accessibilityServiceEnabled: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
) {
    /** Real-time interception is available through at least one monitoring mechanism. */
    val canMonitor: Boolean get() = accessibilityServiceEnabled || usageAccessGranted
}
