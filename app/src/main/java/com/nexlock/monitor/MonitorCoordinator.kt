package com.nexlock.monitor

import android.content.Context
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.core.permissions.PermissionStatusProvider
import com.nexlock.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Decides which monitoring mechanism should run.
 *
 * * Accessibility service enabled -> nothing else is needed (real-time, lowest battery cost).
 * * Accessibility disabled but usage access granted -> foreground polling service.
 * * Protection off -> stop everything.
 */
@Singleton
class MonitorCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val permissionStatusProvider: PermissionStatusProvider,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun sync() = withContext(dispatchers.io) {
        val settings = settingsRepository.current()
        val accessibilityEnabled = permissionStatusProvider.isAccessibilityServiceEnabled()
        val usageAccess = permissionStatusProvider.hasUsageAccess()

        if (settings.protectionEnabled && !accessibilityEnabled && usageAccess) {
            AppMonitorService.start(context)
        } else {
            AppMonitorService.stop(context)
        }
    }
}
