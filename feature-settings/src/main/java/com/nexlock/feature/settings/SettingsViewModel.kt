package com.nexlock.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexlock.common.model.AppLanguage
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.model.ThemeMode
import com.nexlock.core.events.SecurityEvent
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.core.security.SecurityPosture
import com.nexlock.core.security.SecurityPostureRepository
import com.nexlock.core.settings.AppSettings
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.core.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val posture: SecurityPosture? = null,
    val eventCount: Int = 0,
    val vaultBytes: Long = 0L,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val postureRepository: SecurityPostureRepository,
    private val eventsRepository: SecurityEventRepository,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(
        postureRepository.posture,
        eventsRepository.observeCount(),
        vaultRepository.observeTotalBytes(),
        local,
    ) { posture, eventCount, vaultBytes, localState ->
        localState.copy(
            settings = posture.settings,
            posture = posture,
            eventCount = eventCount,
            vaultBytes = vaultBytes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun refresh() = postureRepository.refresh()

    fun setTheme(mode: ThemeMode) = launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { settingsRepository.setDynamicColor(enabled) }
    fun setHaptics(enabled: Boolean) = launch { settingsRepository.setHapticsEnabled(enabled) }
    fun setLanguage(language: AppLanguage) = launch { settingsRepository.setLanguage(language) }
    fun setScreenshotsBlocked(enabled: Boolean) = launch { settingsRepository.setScreenshotsBlocked(enabled) }
    fun setSecureClipboard(enabled: Boolean) = launch { settingsRepository.setSecureClipboardEnabled(enabled) }
    fun setLockTimeout(seconds: Int) = launch { settingsRepository.setLockTimeoutSeconds(seconds) }
    fun setRelockOnScreenOff(enabled: Boolean) = launch { settingsRepository.setRelockOnScreenOff(enabled) }
    fun setShowRiskWarnings(enabled: Boolean) = launch { settingsRepository.setShowRiskWarnings(enabled) }
    fun setLogRetention(days: Int) = launch { settingsRepository.setSecurityLogRetentionDays(days) }

    fun setNotificationsEnabled(enabled: Boolean) = launch { settingsRepository.setNotificationsEnabled(enabled) }
    fun setNotifyIncidents(enabled: Boolean) = launch { settingsRepository.setNotifyIncidents(enabled) }
    fun setNotifyFailedAuth(enabled: Boolean) = launch { settingsRepository.setNotifyFailedAuth(enabled) }
    fun setNotifyPermissionIssues(enabled: Boolean) = launch {
        settingsRepository.setNotifyPermissionIssues(enabled)
    }
    fun setNotifyProtectionState(enabled: Boolean) = launch {
        settingsRepository.setNotifyProtectionState(enabled)
    }

    fun setProtectionEnabled(enabled: Boolean) = launch {
        if (enabled && !settingsRepository.current().credentialConfigured) {
            local.update {
                it.copy(error = "Set up an unlock credential before enabling protection")
            }
            return@launch
        }
        settingsRepository.setProtectionEnabled(enabled)
        refresh()
    }

    fun clearSecurityLog() {
        viewModelScope.launch {
            eventsRepository.clearAll()
            local.update { it.copy(message = "Security log cleared") }
        }
    }

    fun pruneSecurityLog() {
        viewModelScope.launch { eventsRepository.pruneExpired() }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }
    fun consumeError() = local.update { it.copy(error = null) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { throwable ->
                    local.update { it.copy(error = throwable.message ?: "Unable to save the setting") }
                }
        }
    }
}

data class SecurityLogUiState(
    val events: List<SecurityEvent> = emptyList(),
    val filter: SecuritySeverity? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class SecurityLogViewModel @Inject constructor(
    private val eventsRepository: SecurityEventRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<SecuritySeverity?>(null)

    val state: StateFlow<SecurityLogUiState> = combine(
        eventsRepository.observeRecent(300),
        filter,
    ) { events, severity ->
        SecurityLogUiState(
            events = if (severity == null) events else events.filter { it.severity == severity },
            filter = severity,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SecurityLogUiState(),
    )

    fun setFilter(severity: SecuritySeverity?) {
        filter.value = severity
    }

    fun clear() {
        viewModelScope.launch { eventsRepository.clearAll() }
    }
}

/** Wrapper so byte arrays can live in state without structural-equality surprises. */
class BytesHolder(val bytes: ByteArray)

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val pendingBackup: BytesHolder? = null,
    val loadedRestore: BytesHolder? = null,
    val restoreSummary: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backend: BackupController,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    fun createBackup(passphrase: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            backend.createBackup(passphrase)
                .onSuccess { bytes ->
                    _state.update {
                        it.copy(busy = false, pendingBackup = BytesHolder(bytes))
                    }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(busy = false, error = throwable.message ?: "Backup failed") }
                }
        }
    }

    fun saveBackupTo(uri: android.net.Uri) {
        val bytes = _state.value.pendingBackup?.bytes ?: return
        viewModelScope.launch {
            backend.writeBytes(uri, bytes)
                .onSuccess {
                    _state.update {
                        it.copy(pendingBackup = null, message = "Encrypted backup saved")
                    }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(error = throwable.message ?: "Could not write the backup file") }
                }
        }
    }

    fun loadRestoreFile(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            backend.readBytes(uri)
                .onSuccess { bytes -> _state.update { it.copy(busy = false, loadedRestore = BytesHolder(bytes)) } }
                .onFailure { throwable ->
                    _state.update { it.copy(busy = false, error = throwable.message ?: "Could not read the file") }
                }
        }
    }

    fun restore(passphrase: String) {
        val bytes = _state.value.loadedRestore?.bytes ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            backend.restore(bytes, passphrase)
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            busy = false,
                            loadedRestore = null,
                            message = "Backup restored",
                            restoreSummary = "Restored ${summary.lockedApps} apps, ${summary.notes} notes, " +
                                "${summary.schedules} schedules, ${summary.trustedNetworks} networks and " +
                                "${summary.locationRules} places.",
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(busy = false, error = throwable.message ?: "Restore failed") }
                }
        }
    }

    fun dismissRestore() = _state.update { it.copy(loadedRestore = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeError() = _state.update { it.copy(error = null) }
}
