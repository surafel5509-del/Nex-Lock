package com.nexlock.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexlock.common.util.NexLog
import com.nexlock.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores the fallback monitor after a reboot or an app update.
 *
 * Android 12+ may refuse a foreground-service start from the background. That is expected and
 * handled: the user is never left thinking protection is running when it is not - the dashboard and
 * the settings screen always read the real service/permission state.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var monitorCoordinator: MonitorCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val settings = settingsRepository.current()
                if (settings.protectionEnabled) {
                    monitorCoordinator.sync()
                }
            } catch (e: Exception) {
                NexLog.w(TAG, "Unable to restore monitoring after boot", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
