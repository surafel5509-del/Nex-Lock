package com.nexlock.monitor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nexlock.LockLauncher
import com.nexlock.MainActivity
import com.nexlock.R
import com.nexlock.common.util.NexConstants
import com.nexlock.common.util.NexLog
import com.nexlock.core.apps.LockDecision
import com.nexlock.core.apps.LockEngine
import com.nexlock.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground fallback monitor used when the accessibility service is not enabled.
 *
 * It reads the foreground package from UsageStats (requires the user-granted usage-access special
 * permission) and asks [LockEngine] for a decision. The service is intentionally lightweight: one
 * fixed-interval poll, no wake locks, and it stops itself as soon as protection is switched off.
 */
@AndroidEntryPoint
class AppMonitorService : Service() {

    @Inject
    lateinit var lockEngine: LockEngine

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private var lastForegroundPackage: String? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        registerScreenReceiver()
        pollingJob = scope.launch { pollForeground() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        runCatching { screenReceiver?.let { unregisterReceiver(it) } }
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NexConstants.MONITOR_NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            NexLog.e(TAG, "Unable to enter the foreground state", e)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NexConstants.MONITOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(contentIntent)
            .build()
    }

    private suspend fun pollForeground() {
        while (scope.isActive) {
            val settings = settingsRepository.current()
            if (!settings.protectionEnabled) {
                stopSelf()
                return
            }
            val foreground = detectForegroundPackage()
            if (foreground != null && foreground != lastForegroundPackage) {
                lastForegroundPackage = foreground
                when (val decision = lockEngine.onForegroundApp(foreground)) {
                    is LockDecision.Lock -> LockLauncher.show(this, decision)
                    LockDecision.Ignore -> Unit
                }
            }
            delay(NexConstants.MONITOR_POLL_INTERVAL_MS)
        }
    }

    private fun detectForegroundPackage(): String? = runCatching {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - QUERY_WINDOW_MILLIS, now) ?: return null
        val event = UsageEvents.Event()
        var candidate: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == FOREGROUND_EVENT_TYPE) {
                candidate = event.packageName
            }
        }
        candidate
    }.onFailure { NexLog.w(TAG, "Foreground detection failed", it) }.getOrNull()

    private fun registerScreenReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    scope.launch {
                        runCatching {
                            if (settingsRepository.current().relockOnScreenOff) {
                                lockEngine.relockEverything()
                            }
                        }.onFailure { NexLog.w(TAG, "Screen-off relock failed", it) }
                    }
                }
            }
        }
        screenReceiver = receiver
        runCatching {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, filter)
            }
        }.onFailure { NexLog.w(TAG, "Unable to observe screen state", it) }
    }

    companion object {
        private const val TAG = "AppMonitor"
        private const val QUERY_WINDOW_MILLIS = 10_000L

        // UsageEvents.Event.ACTIVITY_RESUMED (API 29+) and the legacy MOVE_TO_FOREGROUND are both 1.
        private const val FOREGROUND_EVENT_TYPE = 1

        fun start(context: Context) {
            val intent = Intent(context, AppMonitorService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                NexLog.w(TAG, "Foreground monitor could not start", e)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AppMonitorService::class.java)) }
        }
    }
}
