package com.nexlock.feature.auth

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexlock.common.model.AuthMode
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.core.auth.AuthRepository
import com.nexlock.core.auth.AuthState
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.security.biometric.BiometricAuthManager
import com.nexlock.security.biometric.BiometricPromptConfig
import com.nexlock.security.biometric.BiometricResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val mode: AuthMode = AuthMode.PIN,
    val busy: Boolean = false,
    val error: String? = null,
    val recoveryCode: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class AuthSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    fun selectMode(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null) }

    fun submit(secret: String, confirmation: String) {
        if (secret != confirmation) {
            _state.update { it.copy(error = "The two entries do not match") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            authRepository.setupCredential(_state.value.mode, secret)
                .onSuccess { recoveryCode ->
                    settingsRepository.setOnboardingCompleted(true)
                    _state.update { it.copy(busy = false, recoveryCode = recoveryCode) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(busy = false, error = readableMessage(throwable)) }
                }
        }
    }

    /**
     * Finishes onboarding. Skipping still marks onboarding as complete (so the gate is not shown
     * again) but deliberately leaves protection off - nothing can be locked without a credential.
     */
    fun complete(onboarding: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            if (onboarding) {
                settingsRepository.setProtectionEnabled(true)
            }
            _state.update { it.copy(completed = true) }
        }
    }

    fun clearRecoveryCode() = _state.update { it.copy(recoveryCode = null) }
}

data class AuthManageUiState(
    val authState: AuthState? = null,
    val biometricAvailable: Boolean = false,
    val biometricEnabling: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AuthManageViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val events: SecurityEventRepository,
    private val biometricAuthManager: BiometricAuthManager,
) : ViewModel() {

    private val _local = MutableStateFlow(AuthManageUiState(biometricAvailable = biometricAuthManager.isAvailable()))
    val local: StateFlow<AuthManageUiState> = _local

    val authState: StateFlow<AuthState?> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun enableBiometric(activity: FragmentActivity) {
        if (_local.value.biometricEnabling) return
        viewModelScope.launch {
            _local.update { it.copy(biometricEnabling = true, error = null, message = null) }
            biometricAuthManager.authenticate(
                activity,
                BiometricPromptConfig(
                    title = "Enable biometric unlock",
                    subtitle = "Verify it's you to turn on biometric unlock",
                ),
            ).collect { result ->
                when (result) {
                    BiometricResult.Success -> {
                        settingsRepository.setBiometricEnabled(true)
                        events.log(
                            SecurityEventType.BIOMETRIC_ENABLED,
                            SecuritySeverity.INFO,
                            "Biometric unlock enabled",
                        )
                        _local.update {
                            it.copy(biometricEnabling = false, message = "Biometric unlock is on")
                        }
                    }
                    is BiometricResult.Error -> _local.update {
                        it.copy(biometricEnabling = false, error = result.message)
                    }
                    is BiometricResult.Unavailable -> _local.update {
                        it.copy(
                            biometricEnabling = false,
                            error = "Biometric hardware is not available. Set up a fingerprint in system settings first.",
                        )
                    }
                    BiometricResult.Cancelled -> _local.update { it.copy(biometricEnabling = false) }
                }
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            settingsRepository.setBiometricEnabled(false)
            events.log(
                SecurityEventType.BIOMETRIC_DISABLED,
                SecuritySeverity.INFO,
                "Biometric unlock disabled",
            )
            _local.update { it.copy(message = "Biometric unlock is off") }
        }
    }
}

data class ChangeCredentialUiState(
    val mode: AuthMode = AuthMode.PIN,
    val busy: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class ChangeCredentialViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangeCredentialUiState())
    val state: StateFlow<ChangeCredentialUiState> = _state

    fun selectMode(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null) }

    fun submit(currentSecret: String, newSecret: String, confirmation: String) {
        if (newSecret != confirmation) {
            _state.update { it.copy(error = "The two new entries do not match") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            authRepository.changeCredential(currentSecret, _state.value.mode, newSecret)
                .onSuccess { _state.update { it.copy(busy = false, success = true) } }
                .onFailure { throwable ->
                    _state.update { it.copy(busy = false, error = readableMessage(throwable)) }
                }
        }
    }
}

internal fun readableMessage(throwable: Throwable): String =
    throwable.message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
