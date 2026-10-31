package com.nexlock.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.util.NexConstants
import com.nexlock.common.util.NexLog
import com.nexlock.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All notifications pass through here so we can guarantee a single privacy rule:
 * **notifications never contain protected application names or private content.**
 */
@Singleton
class NexNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                NexConstants.CHANNEL_SECURITY,
                "Security alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Failed unlock attempts, intruder detections and tampering warnings" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NexConstants.CHANNEL_STATUS,
                "Protection status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shown while NexLock is actively protecting your apps" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NexConstants.MONITOR_CHANNEL_ID,
                "Background protection",
                NotificationManager.IMPORTANCE_MIN,
            ).apply { description = "Keeps the protection service running" }
        )
    }

    suspend fun notifyForEvent(type: SecurityEventType, severity: SecuritySeverity) {
        val settings = settingsRepository.current()
        if (!settings.notificationsEnabled) return
        val (title, body) = when (type) {
            SecurityEventType.AUTH_FAILURE ->
                if (!settings.notifyFailedAuth) return else "Failed unlock attempt" to
                    "Someone entered an incorrect unlock credential."
            SecurityEventType.LOCKOUT_TRIGGERED ->
                if (!settings.notifyFailedAuth) return else "Unlock temporarily locked" to
                    "Too many incorrect attempts. Unlocking is paused for a while."
            SecurityEventType.INTRUDER_CAPTURED ->
                if (!settings.notifyIncidents) return else "Intruder incident recorded" to
                    "A failed attempt was recorded in your private incident history."
            SecurityEventType.RISK_DETECTED ->
                if (!settings.notifyPermissionIssues) return else "Security warning" to
                    "Your device shows signals that can weaken app-level protection."
            else -> return
        }
        post(title, body, NexConstants.NOTIFICATION_SECURITY_ID + type.ordinal, NexConstants.CHANNEL_SECURITY)
    }

    suspend fun notifyPermissionIssue(message: String) {
        val settings = settingsRepository.current()
        if (!settings.notificationsEnabled || !settings.notifyPermissionIssues) return
        post("Protection needs attention", message, NexConstants.NOTIFICATION_SECURITY_ID + 100, NexConstants.CHANNEL_SECURITY)
    }

    suspend fun notifyProtectionState(enabled: Boolean) {
        val settings = settingsRepository.current()
        if (!settings.notificationsEnabled || !settings.notifyProtectionState) {
            cancel(NexConstants.NOTIFICATION_STATUS_ID)
            return
        }
        if (enabled) {
            post(
                "Protection is on",
                "NexLock is guarding your selected apps.",
                NexConstants.NOTIFICATION_STATUS_ID,
                NexConstants.CHANNEL_STATUS,
            )
        } else {
            cancel(NexConstants.NOTIFICATION_STATUS_ID)
        }
    }

    fun cancel(id: Int) = runCatching { NotificationManagerCompat.from(context).cancel(id) }

    fun cancelAll() = runCatching { NotificationManagerCompat.from(context).cancelAll() }

    private fun post(title: String, body: String, id: Int, channelId: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            NexLog.w(TAG, "Notification permission missing", e)
        }
    }

    companion object {
        private const val TAG = "Notifier"
    }
}
