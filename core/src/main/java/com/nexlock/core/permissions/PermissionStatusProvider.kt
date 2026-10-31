package com.nexlock.core.permissions

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the real state of every permission NexLock depends on. No permission is requested
 * implicitly; each screen explains the benefit before sending the user to system settings.
 */
@Singleton
class PermissionStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun status(): PermissionStatus = PermissionStatus(
        accessibilityServiceEnabled = isAccessibilityServiceEnabled(),
        usageAccessGranted = hasUsageAccess(),
        notificationsGranted = areNotificationsEnabled(),
        cameraGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED,
        locationGranted = hasLocationPermission(),
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(),
    )

    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedSuffix = "$APP_PACKAGE.$ACCESSIBILITY_SERVICE_CLASS"
        val enabledFromSettings = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull().orEmpty()
        if (enabledFromSettings.contains(expectedSuffix)) return true

        // Fallback for OEMs that report services only through AccessibilityManager.
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.name == ACCESSIBILITY_SERVICE_CLASS && it.resolveInfo.serviceInfo.packageName == APP_PACKAGE }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                APP_PACKAGE,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                APP_PACKAGE,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun areNotificationsEnabled(): Boolean = runCatching {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }.getOrDefault(false)

    fun isIgnoringBatteryOptimizations(): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(APP_PACKAGE)
    }

    companion object {
        const val APP_PACKAGE = "com.nexlock"
        const val ACCESSIBILITY_SERVICE_CLASS = "com.nexlock.monitor.AppMonitorAccessibilityService"
    }
}
