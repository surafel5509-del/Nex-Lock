package com.nexlock.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexlock.common.model.RiskReport
import com.nexlock.common.model.SecurityLevel
import com.nexlock.common.model.SecurityRecommendation
import com.nexlock.core.apps.LockedApp
import com.nexlock.core.apps.LockedAppsRepository
import com.nexlock.core.automation.AutomationRepository
import com.nexlock.core.automation.AutomationSnapshot
import com.nexlock.core.events.SecurityEvent
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.core.permissions.PermissionStatus
import com.nexlock.core.security.SecurityPostureRepository
import com.nexlock.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val protectionEnabled: Boolean = false,
    val score: Int = 0,
    val level: SecurityLevel = SecurityLevel.AT_RISK,
    val lockedCount: Int = 0,
    val recentEvents: List<SecurityEvent> = emptyList(),
    val protectedApps: List<LockedApp> = emptyList(),
    val recommendations: List<SecurityRecommendation> = emptyList(),
    val risk: RiskReport? = null,
    val permissions: PermissionStatus = PermissionStatus(),
    val temporaryUnlockUntilMillis: Long = 0L,
    val hardwareBackedKey: Boolean = false,
    val automation: AutomationSnapshot? = null,
    val biometricEnabled: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val postureRepository: SecurityPostureRepository,
    private val lockedAppsRepository: LockedAppsRepository,
    private val eventsRepository: SecurityEventRepository,
    private val automationRepository: AutomationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<DashboardUiState> = combine(
        postureRepository.posture,
        eventsRepository.observeRecent(EVENT_LIMIT),
        lockedAppsRepository.lockedOnly,
    ) { posture, events, apps ->
        DashboardUiState(
            loading = false,
            protectionEnabled = posture.settings.protectionEnabled,
            score = posture.score.score,
            level = posture.score.level,
            lockedCount = posture.lockedAppsCount,
            recentEvents = events,
            protectedApps = apps,
            recommendations = posture.recommendations,
            risk = posture.risk,
            permissions = posture.permissions,
            temporaryUnlockUntilMillis = posture.settings.temporaryUnlockUntilMillis,
            hardwareBackedKey = posture.hardwareBackedKey,
            automation = posture.automation,
            biometricEnabled = posture.settings.biometricEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    fun refresh() = postureRepository.refresh()

    fun setProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !settingsRepository.current().credentialConfigured) {
                // The dashboard surfaces "Set up authentication" as a recommendation instead.
                postureRepository.refresh()
                return@launch
            }
            settingsRepository.setProtectionEnabled(enabled)
            postureRepository.refresh()
        }
    }

    fun lockAll() {
        viewModelScope.launch { lockedAppsRepository.lockAll() }
    }

    fun unlockAll() {
        viewModelScope.launch {
            lockedAppsRepository.unlockAll()
            postureRepository.refresh()
        }
    }

    fun startTemporaryUnlock() {
        viewModelScope.launch {
            val minutes = settingsRepository.current().temporaryUnlockMinutes
            automationRepository.startTemporaryUnlock(minutes)
            postureRepository.refresh()
        }
    }

    fun endTemporaryUnlock() {
        viewModelScope.launch {
            automationRepository.endTemporaryUnlock()
            postureRepository.refresh()
        }
    }

    companion object {
        private const val EVENT_LIMIT = 6
    }
}
