package com.nexlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.core.security.SecurityPostureRepository
import com.nexlock.core.settings.AppSettings
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.monitor.MonitorCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val postureRepository: SecurityPostureRepository,
    private val monitorCoordinator: MonitorCoordinator,
    private val eventsRepository: SecurityEventRepository,
) : ViewModel() {

    val state: StateFlow<AppUiState> = settingsRepository.settings
        .map { AppUiState(loading = false, settings = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppUiState(),
        )

    init {
        // Keep the background monitor aligned with the protection preference.
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.protectionEnabled }
                .distinctUntilChanged()
                .collect { monitorCoordinator.sync() }
        }
    }

    fun onResume() {
        postureRepository.refresh()
        viewModelScope.launch {
            monitorCoordinator.sync()
            eventsRepository.pruneExpired()
        }
    }

    fun refresh() {
        postureRepository.refresh()
        viewModelScope.launch { monitorCoordinator.sync() }
    }
}
