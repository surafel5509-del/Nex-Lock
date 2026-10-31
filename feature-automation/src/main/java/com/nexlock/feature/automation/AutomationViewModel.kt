package com.nexlock.feature.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexlock.common.policy.ScheduleCalculator
import com.nexlock.core.automation.AutomationRepository
import com.nexlock.core.automation.AutomationSnapshot
import com.nexlock.core.automation.LockSchedule
import com.nexlock.core.automation.LocationRule
import com.nexlock.core.automation.TrustedNetwork
import com.nexlock.core.permissions.PermissionStatusProvider
import com.nexlock.core.profiles.LockProfile
import com.nexlock.core.profiles.ProfileRepository
import com.nexlock.core.settings.AppSettings
import com.nexlock.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AutomationUiState(
    val loading: Boolean = true,
    val enabled: Boolean = true,
    val scheduleEnabled: Boolean = false,
    val wifiEnabled: Boolean = false,
    val locationEnabled: Boolean = false,
    val schedules: List<LockSchedule> = emptyList(),
    val networks: List<TrustedNetwork> = emptyList(),
    val locations: List<LocationRule> = emptyList(),
    val profiles: List<LockProfile> = emptyList(),
    val activeProfile: LockProfile? = null,
    val currentSsid: String? = null,
    val temporaryUnlockUntilMillis: Long = 0L,
    val temporaryUnlockMinutes: Int = 5,
    val locationPermissionGranted: Boolean = false,
    val snapshot: AutomationSnapshot? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val temporaryUnlockActive: Boolean
        get() = temporaryUnlockUntilMillis > System.currentTimeMillis()
}

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val automationRepository: AutomationRepository,
    private val profileRepository: ProfileRepository,
    private val permissionStatusProvider: PermissionStatusProvider,
) : ViewModel() {

    private data class DataBundle(
        val settings: AppSettings,
        val schedules: List<LockSchedule>,
        val networks: List<TrustedNetwork>,
        val locations: List<LocationRule>,
    )

    private val local = MutableStateFlow(
        AutomationUiState(
            locationPermissionGranted = permissionStatusProvider.status().locationGranted,
        )
    )

    private val bundle = combine(
        settingsRepository.settings,
        automationRepository.schedules,
        automationRepository.trustedNetworks,
        automationRepository.locationRules,
    ) { settings, schedules, networks, locations ->
        DataBundle(settings, schedules, networks, locations)
    }

    val state: StateFlow<AutomationUiState> = combine(
        bundle,
        profileRepository.profiles,
        local,
    ) { data, profiles, localState ->
        localState.copy(
            loading = false,
            enabled = data.settings.automationEnabled,
            scheduleEnabled = data.settings.scheduleEnabled,
            wifiEnabled = data.settings.wifiAutomationEnabled,
            locationEnabled = data.settings.locationAutomationEnabled,
            schedules = data.schedules,
            networks = data.networks,
            locations = data.locations,
            profiles = profiles,
            activeProfile = profiles.firstOrNull { it.isActive },
            temporaryUnlockUntilMillis = data.settings.temporaryUnlockUntilMillis,
            temporaryUnlockMinutes = data.settings.temporaryUnlockMinutes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AutomationUiState(),
    )

    init {
        viewModelScope.launch { profileRepository.ensureDefaultProfile() }
        refreshContext()
    }

    fun refreshContext() {
        viewModelScope.launch {
            val snapshot = automationRepository.evaluateNow(forceRefresh = true)
            val ssid = automationRepository.currentSsid()
            local.update {
                it.copy(
                    snapshot = snapshot,
                    currentSsid = ssid,
                    locationPermissionGranted = permissionStatusProvider.status().locationGranted,
                )
            }
        }
    }

    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutomationEnabled(enabled)
            automationRepository.invalidateCache()
            refreshContext()
        }
    }

    fun setScheduleEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setScheduleEnabled(enabled)
        automationRepository.invalidateCache()
        refreshContext()
    }

    fun setWifiEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setWifiAutomationEnabled(enabled)
        automationRepository.invalidateCache()
        refreshContext()
    }

    fun setLocationEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLocationAutomationEnabled(enabled)
        automationRepository.invalidateCache()
        refreshContext()
    }

    fun addSchedule(label: String, startMinute: Int, endMinute: Int, daysMask: Int) {
        viewModelScope.launch {
            automationRepository.addSchedule(
                label.ifBlank { "Schedule" },
                startMinute,
                endMinute,
                if (daysMask == 0) ScheduleCalculator.ALL_DAYS_MASK else daysMask,
            )
            settingsRepository.setScheduleEnabled(true)
            local.update { it.copy(message = "Schedule added") }
            refreshContext()
        }
    }

    fun toggleSchedule(schedule: LockSchedule) {
        viewModelScope.launch { automationRepository.setScheduleEnabled(schedule.id, !schedule.enabled) }
    }

    fun deleteSchedule(schedule: LockSchedule) {
        viewModelScope.launch { automationRepository.deleteSchedule(schedule.id) }
    }

    fun addCurrentNetwork() {
        viewModelScope.launch {
            val ssid = automationRepository.currentSsid()
            if (ssid == null) {
                local.update {
                    it.copy(
                        error = if (permissionStatusProvider.status().locationGranted) {
                            "Connect to Wi-Fi first - the network name could not be read"
                        } else {
                            "Location permission is required by Android to read the Wi-Fi name"
                        },
                    )
                }
                return@launch
            }
            automationRepository.addTrustedNetwork(ssid)
            settingsRepository.setWifiAutomationEnabled(true)
            local.update { it.copy(message = "Trusted network added", error = null) }
            refreshContext()
        }
    }

    fun toggleNetwork(network: TrustedNetwork) {
        viewModelScope.launch { automationRepository.setTrustedNetworkEnabled(network.ssid, !network.enabled) }
    }

    fun deleteNetwork(network: TrustedNetwork) {
        viewModelScope.launch { automationRepository.removeTrustedNetwork(network.ssid) }
    }

    fun addCurrentLocation(radiusMeters: Float) {
        viewModelScope.launch {
            automationRepository.addLocationFromCurrent("Trusted place", radiusMeters)
                .onSuccess {
                    settingsRepository.setLocationAutomationEnabled(true)
                    local.update { state -> state.copy(message = "Trusted location saved", error = null) }
                    refreshContext()
                }
                .onFailure { throwable ->
                    local.update {
                        it.copy(error = throwable.message ?: "Unable to read the current location")
                    }
                }
        }
    }

    fun toggleLocation(rule: LocationRule) {
        viewModelScope.launch { automationRepository.setLocationRuleEnabled(rule.id, !rule.enabled) }
    }

    fun deleteLocation(rule: LocationRule) {
        viewModelScope.launch { automationRepository.deleteLocationRule(rule.id) }
    }

    fun startTemporaryUnlock(minutes: Int) {
        viewModelScope.launch {
            automationRepository.startTemporaryUnlock(minutes)
            local.update { it.copy(message = "Temporary unlock started") }
            refreshContext()
        }
    }

    fun endTemporaryUnlock() {
        viewModelScope.launch {
            automationRepository.endTemporaryUnlock()
            refreshContext()
        }
    }

    fun addProfile(name: String, timeoutSeconds: Int) {
        viewModelScope.launch { profileRepository.addProfile(name, timeoutSeconds) }
    }

    fun activateProfile(profile: LockProfile) {
        viewModelScope.launch {
            profileRepository.activate(profile)
            local.update { it.copy(message = "${profile.name} activated") }
        }
    }

    fun deleteProfile(profile: LockProfile) {
        viewModelScope.launch { profileRepository.delete(profile) }
    }

    fun setTemporaryUnlockMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setTemporaryUnlockMinutes(minutes) }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    fun consumeError() = local.update { it.copy(error = null) }
}
