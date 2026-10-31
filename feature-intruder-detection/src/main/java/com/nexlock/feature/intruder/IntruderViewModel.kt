package com.nexlock.feature.intruder

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexlock.core.intruder.IntruderIncident
import com.nexlock.core.intruder.IntruderRepository
import com.nexlock.core.permissions.PermissionStatusProvider
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

data class IntruderUiState(
    val enabled: Boolean = false,
    val photoEnabled: Boolean = false,
    val threshold: Int = 3,
    val cameraGranted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val incidents: List<IntruderIncident> = emptyList(),
    val loading: Boolean = true,
    val preview: Pair<IntruderIncident, Bitmap>? = null,
    val error: String? = null,
)

@HiltViewModel
class IntruderViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val intruderRepository: IntruderRepository,
    private val permissionStatusProvider: PermissionStatusProvider,
) : ViewModel() {

    private val local = MutableStateFlow(
        IntruderUiState(cameraGranted = permissionStatusProvider.status().cameraGranted)
    )

    val state: StateFlow<IntruderUiState> = combine(
        settingsRepository.settings,
        intruderRepository.observeIncidents(),
        local,
    ) { settings, incidents, localState ->
        localState.copy(
            enabled = settings.intruderDetectionEnabled,
            photoEnabled = settings.intruderPhotoEnabled,
            threshold = settings.intruderFailureThreshold,
            notificationsEnabled = settings.notificationsEnabled,
            incidents = incidents,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IntruderUiState(),
    )

    fun refreshPermissions() {
        local.update { it.copy(cameraGranted = permissionStatusProvider.status().cameraGranted) }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIntruderDetectionEnabled(enabled)
            if (!enabled) settingsRepository.setIntruderPhotoEnabled(false)
        }
    }

    fun setPhotoEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !permissionStatusProvider.status().cameraGranted) {
                local.update { it.copy(error = "Grant camera permission to capture intruder photos") }
                return@launch
            }
            settingsRepository.setIntruderPhotoEnabled(enabled)
        }
    }

    fun setThreshold(value: Int) {
        viewModelScope.launch { settingsRepository.setIntruderFailureThreshold(value) }
    }

    fun openPhoto(incident: IntruderIncident) {
        if (!incident.hasPhoto) return
        viewModelScope.launch {
            intruderRepository.loadPhoto(incident.id)
                .onSuccess { bitmap -> local.update { it.copy(preview = incident to bitmap) } }
                .onFailure { throwable ->
                    local.update { it.copy(error = throwable.message ?: "Capture is unavailable") }
                }
        }
    }

    fun closePreview() = local.update { it.copy(preview = null, error = null) }

    fun deleteIncident(incident: IntruderIncident) {
        viewModelScope.launch { intruderRepository.deleteIncident(incident.id) }
    }

    fun clearAll() {
        viewModelScope.launch { intruderRepository.clearAll() }
    }

    fun dismissError() = local.update { it.copy(error = null) }
}
