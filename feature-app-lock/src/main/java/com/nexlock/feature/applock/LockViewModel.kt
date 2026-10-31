package com.nexlock.feature.applock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexlock.common.model.AuthMode
import com.nexlock.core.apps.LockEngine
import com.nexlock.core.apps.LockedAppsRepository
import com.nexlock.core.auth.AuthOutcome
import com.nexlock.core.auth.AuthRepository
import com.nexlock.core.intruder.IntruderRepository
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.core.apps.UnlockSessionManager
import com.nexlock.security.biometric.BiometricAuthManager
import com.nexlock.security.biometric.BiometricPromptConfig
import com.nexlock.security.biometric.BiometricResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockUiState(
    val packageName: String = "",
    val appName: String = "",
    val mode: AuthMode = AuthMode.PIN,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val error: String? = null,
    val attemptsRemaining: Int? = null,
    val lockoutUntilMillis: Long = 0L,
    val busy: Boolean = false,
    val unlocked: Boolean = false,
    val captureRequest: Long = 0L,
    val intruderPhotoEnabled: Boolean = false,
    val screenshotsBlocked: Boolean = true,
    val recoveryVisible: Boolean = false,
    val recoveryNewCode: String? = null,
)

sealed interface LockUiEvent {
    data object Unlocked : LockUiEvent
    data object BackToHome : LockUiEvent
}

@HiltViewModel
class LockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val lockEngine: LockEngine,
    private val lockedAppsRepository: LockedAppsRepository,
    private val settingsRepository: SettingsRepository,
    private val intruderRepository: IntruderRepository,
    private val biometricAuthManager: BiometricAuthManager,
    private val sessionManager: UnlockSessionManager,
) : ViewModel() {

    private val packageArg: String = savedStateHandle.get<String>(ARG_PACKAGE).orEmpty()
    private val nameArg: String = savedStateHandle.get<String>(ARG_APP_NAME).orEmpty()

    private val _state = MutableStateFlow(
        LockUiState(packageName = packageArg, appName = nameArg.ifBlank { "Protected app" })
    )
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private val events = Channel<LockUiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    private var failureCount = 0
    private var captureTriggered = false

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val biometricAvailable = biometricAuthManager.isAvailable()
            val resolvedName = nameArg.ifBlank {
                lockedAppsRepository.appName(packageArg).orEmpty()
            }
            _state.update {
                it.copy(
                    appName = resolvedName.ifBlank { "Protected app" },
                    mode = settings.authMode,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = settings.biometricEnabled && biometricAvailable,
                    lockoutUntilMillis = settings.lockoutUntilMillis,
                    intruderPhotoEnabled = settings.intruderDetectionEnabled && settings.intruderPhotoEnabled,
                    screenshotsBlocked = settings.screenshotsBlocked,
                )
            }
        }
    }

    fun submit(secret: String) {
        if (secret.isBlank() || _state.value.busy || _state.value.unlocked) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val outcome = authRepository.verify(secret)) {
                is AuthOutcome.Success -> unlock()
                is AuthOutcome.Invalid -> onFailure(outcome.attemptsBeforeLockout)
                is AuthOutcome.LockedOut -> _state.update {
                    it.copy(
                        busy = false,
                        error = "Too many incorrect attempts",
                        lockoutUntilMillis = System.currentTimeMillis() + outcome.remainingMillis,
                    )
                }
                is AuthOutcome.NotConfigured -> _state.update {
                    it.copy(busy = false, error = "Set up an unlock credential in NexLock first")
                }
                is AuthOutcome.Error -> _state.update { it.copy(busy = false, error = outcome.message) }
            }
        }
    }

    fun authenticateWithBiometrics(activity: androidx.fragment.app.FragmentActivity) {
        if (_state.value.busy || _state.value.unlocked) return
        viewModelScope.launch {
            val modeLabel = _state.value.mode.name.lowercase().replaceFirstChar { it.uppercase() }
            biometricAuthManager.authenticate(
                activity,
                BiometricPromptConfig(
                    title = "Unlock ${_state.value.appName}",
                    subtitle = "Confirm it's you to continue",
                    negativeButtonText = "Use $modeLabel",
                ),
            ).collect { result ->
                when (result) {
                    BiometricResult.Success -> {
                        authRepository.onBiometricSuccess()
                        unlock()
                    }
                    is BiometricResult.Error -> _state.update { it.copy(error = result.message) }
                    is BiometricResult.Unavailable -> _state.update {
                        it.copy(
                            biometricEnabled = false,
                            error = "Biometric unlock is unavailable on this device",
                        )
                    }
                    BiometricResult.Cancelled -> Unit
                }
            }
        }
    }

    fun showRecovery(visible: Boolean) {
        _state.update { it.copy(recoveryVisible = visible, error = null) }
    }

    fun recoverWithCode(code: String, newSecret: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val mode = _state.value.mode
            authRepository.recover(code, mode, newSecret)
                .onSuccess { newRecoveryCode ->
                    _state.update { it.copy(recoveryNewCode = newRecoveryCode) }
                    unlock()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(busy = false, error = throwable.message ?: "Recovery failed")
                    }
                }
        }
    }

    fun onCaptureSaved(file: File) {
        viewModelScope.launch {
            intruderRepository.recordCapture(file, failureCount, packageArg)
        }
    }

    fun onCaptureFailed(reason: String) {
        if (captureTriggered) return
        captureTriggered = true
        viewModelScope.launch {
            intruderRepository.recordIncident(failureCount, packageArg, reason)
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun requestHome() {
        viewModelScope.launch { events.send(LockUiEvent.BackToHome) }
    }

    private suspend fun unlock() {
        sessionManager.grantUnlock(packageArg)
        lockEngine.onUnlockSucceeded(packageArg)
        _state.update { it.copy(busy = false, unlocked = true) }
        events.send(LockUiEvent.Unlocked)
    }

    private suspend fun onFailure(attemptsBeforeLockout: Int) {
        failureCount += 1
        val settings = settingsRepository.current()
        _state.update {
            it.copy(
                busy = false,
                attemptsRemaining = attemptsBeforeLockout,
                error = if (attemptsBeforeLockout > 0) {
                    "Incorrect credential · $attemptsBeforeLockout attempt(s) left"
                } else {
                    "Incorrect credential"
                },
            )
        }
        val threshold = settings.intruderFailureThreshold.coerceAtLeast(2)
        if (settings.intruderDetectionEnabled && failureCount >= threshold && !captureTriggered) {
            captureTriggered = true
            if (settings.intruderPhotoEnabled) {
                _state.update { it.copy(captureRequest = System.currentTimeMillis()) }
            } else {
                intruderRepository.recordIncident(failureCount, packageArg, null)
            }
        }
    }

    companion object {
        const val ARG_PACKAGE = "package_name"
        const val ARG_APP_NAME = "app_name"
    }
}
