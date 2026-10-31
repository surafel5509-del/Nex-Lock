package com.nexlock.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Deep links into the exact system screens NexLock depends on. */
object SystemSettingsIntents {

    fun openAccessibility(context: Context) = safeStart(
        context,
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun openUsageAccess(context: Context) = safeStart(
        context,
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun openNotificationSettings(context: Context) = safeStart(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun openBatteryOptimization(context: Context) = safeStart(
        context,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun openAppDetails(context: Context) = safeStart(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun safeStart(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }
}
