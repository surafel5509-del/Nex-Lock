package com.nexlock.security.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED,
}

sealed interface BiometricResult {
    data object Success : BiometricResult
    data object Cancelled : BiometricResult
    data class Error(val code: Int, val message: String, val isLockout: Boolean = false) : BiometricResult
    data class Unavailable(val availability: BiometricAvailability) : BiometricResult
}

data class BiometricPromptConfig(
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val negativeButtonText: String = "Use PIN",
)

/**
 * Thin wrapper over the platform BiometricPrompt.
 *
 * Biometric material is handled entirely by the OS; NexLock only receives
 * success / cancel / error callbacks and never sees, stores or transmits biometric data.
 *
 * `BIOMETRIC_WEAK` is requested deliberately: it includes Class 3 (strong) sensors while still
 * working on older devices whose fingerprints are only Class 2.
 */
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun availability(): BiometricAvailability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.SECURITY_UPDATE_REQUIRED
            else -> BiometricAvailability.UNSUPPORTED
        }
    }

    fun isAvailable(): Boolean = availability() == BiometricAvailability.AVAILABLE

    /**
     * Launches the system prompt and completes with exactly one result.
     * Must be called on the main thread while [activity] is resumed.
     */
    fun authenticate(
        activity: FragmentActivity,
        config: BiometricPromptConfig,
    ): Flow<BiometricResult> = callbackFlow {
        val availability = availability()
        if (availability != BiometricAvailability.AVAILABLE) {
            trySend(BiometricResult.Unavailable(availability))
            close()
            return@callbackFlow
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                trySend(BiometricResult.Success)
                close()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val lockout = errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                    errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    trySend(BiometricResult.Cancelled)
                } else {
                    trySend(BiometricResult.Error(errorCode, errString.toString(), lockout))
                }
                close()
            }

            override fun onAuthenticationFailed() {
                // Single non-matching sample: the system prompt stays open, so nothing to report.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(config.title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .setNegativeButtonText(config.negativeButtonText)
        if (config.subtitle.isNotBlank()) infoBuilder.setSubtitle(config.subtitle)
        if (config.description.isNotBlank()) infoBuilder.setDescription(config.description)

        try {
            prompt.authenticate(infoBuilder.build())
        } catch (e: Exception) {
            trySend(BiometricResult.Error(0, e.message ?: "Biometric prompt could not start"))
            close()
        }

        awaitClose { runCatching { prompt.cancelAuthentication() } }
    }

    companion object {
        private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
}
