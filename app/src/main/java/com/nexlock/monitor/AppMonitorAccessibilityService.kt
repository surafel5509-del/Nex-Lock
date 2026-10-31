package com.nexlock.monitor

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.nexlock.LockLauncher
import com.nexlock.common.util.NexLog
import com.nexlock.core.apps.LockDecision
import com.nexlock.core.apps.LockEngine
import com.nexlock.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Real-time enforcement.
 *
 * The service requests the minimum capability set: window state changes only, no window content,
 * no gestures, no text input. It answers exactly one question - "which package just came to the
 * foreground?" - and hands the decision to [LockEngine].
 */
@AndroidEntryPoint
class AppMonitorAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var lockEngine: LockEngine

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var lastPackage: String? = null

    private var screenReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerScreenReceiver()
        NexLog.i(TAG, "Accessibility monitoring connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (packageName == this.packageName) return
        if (packageName == lastPackage) return
        lastPackage = packageName

        scope.launch {
            when (val decision = lockEngine.onForegroundApp(packageName)) {
                is LockDecision.Lock -> LockLauncher.show(this@AppMonitorAccessibilityService, decision)
                LockDecision.Ignore -> Unit
            }
        }
    }

    override fun onInterrupt() {
        NexLog.w(TAG, "Accessibility monitoring interrupted")
    }

    override fun onDestroy() {
        runCatching { screenReceiver?.let { unregisterReceiver(it) } }
        scope.cancel()
        super.onDestroy()
    }

    /** Mirrors the polling service: screen-off relocks every app when the user asked for it. */
    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_SCREEN_OFF) return
                scope.launch {
                    runCatching {
                        if (settingsRepository.current().relockOnScreenOff) {
                            lockEngine.relockEverything()
                        }
                    }.onFailure { NexLog.w(TAG, "Screen-off relock failed", it) }
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
        private const val TAG = "A11yMonitor"
    }
}
