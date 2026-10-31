package com.nexlock.core.auth

import com.nexlock.common.model.AuthMode
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.policy.CredentialError
import com.nexlock.common.policy.CredentialValidator
import com.nexlock.common.policy.LockoutPolicy
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.common.util.TimeProvider
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.security.crypto.SecretHasher
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Owns the authentication lifecycle: setup, verification, progressive lockout, credential changes
 * and recovery-code based resets.
 *
 * Security properties:
 * * secrets are only ever handled in memory and hashed with a Keystore-peppered PBKDF2 hash;
 * * failed attempts and cool-downs are persisted, so force-stopping the app does not reset them;
 * * recovery codes are single-use and rotated after a successful recovery.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val hasher: SecretHasher,
    private val events: SecurityEventRepository,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    private val random = SecureRandom()

    fun observeAuthState(): Flow<AuthState> = settingsRepository.settings.map { settings ->
        AuthState(
            configured = settings.credentialConfigured,
            recoveryConfigured = settings.recoveryConfigured,
            mode = settings.authMode,
            biometricEnabled = settings.biometricEnabled,
            failedAttempts = settings.failedAttempts,
            lockoutUntilMillis = settings.lockoutUntilMillis,
        )
    }

    suspend fun hasCredential(): Boolean = settingsRepository.current().credentialConfigured

    /** Configures the first credential and returns a freshly generated recovery code. */
    suspend fun setupCredential(mode: AuthMode, secret: String): Result<String> =
        withContext(dispatchers.default) {
            runCatching {
                validateOrThrow(mode, secret)
                settingsRepository.saveCredential(mode, hasher.hash(secret))
                val recoveryCode = generateRecoveryCode()
                settingsRepository.saveRecoveryHash(hasher.hash(normaliseRecoveryCode(recoveryCode)))
                events.log(
                    SecurityEventType.CREDENTIAL_CHANGED,
                    SecuritySeverity.INFO,
                    "Unlock credential configured (${mode.name.lowercase()})",
                )
                recoveryCode
            }
        }

    suspend fun verify(secret: String): AuthOutcome = withContext(dispatchers.default) {
        val now = timeProvider.nowMillis()
        val settings = settingsRepository.current()

        if (now < settings.lockoutUntilMillis) {
            return@withContext AuthOutcome.LockedOut(settings.lockoutUntilMillis - now, settings.failedAttempts)
        }
        if (!settings.credentialConfigured) return@withContext AuthOutcome.NotConfigured

        val storedHash = settingsRepository.credentialHash()
            ?: return@withContext AuthOutcome.NotConfigured

        val matches = try {
            hasher.verify(secret, storedHash)
        } catch (e: Exception) {
            return@withContext AuthOutcome.Error("Credential verification is unavailable")
        }

        if (matches) {
            settingsRepository.resetFailedAttempts()
            events.log(
                SecurityEventType.AUTH_SUCCESS,
                SecuritySeverity.INFO,
                "Authentication succeeded (${settings.authMode.name.lowercase()})",
            )
            AuthOutcome.Success
        } else {
            val attempts = settings.failedAttempts + 1
            val lockoutUntil = LockoutPolicy.lockoutUntil(now, attempts)
            settingsRepository.recordFailedAttempt(attempts, lockoutUntil)
            if (lockoutUntil > now) {
                events.log(
                    SecurityEventType.LOCKOUT_TRIGGERED,
                    SecuritySeverity.CRITICAL,
                    "Too many failed attempts - authentication paused",
                )
                AuthOutcome.LockedOut(lockoutUntil - now, attempts)
            } else {
                events.log(
                    SecurityEventType.AUTH_FAILURE,
                    SecuritySeverity.WARNING,
                    "Incorrect unlock credential entered",
                )
                AuthOutcome.Invalid(attempts, (LockoutPolicy.FREE_ATTEMPTS - attempts).coerceAtLeast(0))
            }
        }
    }

    /** Records a successful biometric unlock and clears the failure counter. */
    suspend fun onBiometricSuccess() = withContext(dispatchers.default) {
        settingsRepository.resetFailedAttempts()
        events.log(SecurityEventType.AUTH_SUCCESS, SecuritySeverity.INFO, "Biometric authentication succeeded")
    }

    suspend fun changeCredential(
        currentSecret: String,
        newMode: AuthMode,
        newSecret: String,
    ): Result<Unit> = withContext(dispatchers.default) {
        runCatching {
            validateOrThrow(newMode, newSecret)
            when (val outcome = verify(currentSecret)) {
                is AuthOutcome.Success -> Unit
                is AuthOutcome.LockedOut -> throw CredentialException(
                    CredentialError.EMPTY,
                    "Locked out for ${outcome.remainingMillis / 1000}s",
                )
                else -> throw CredentialException(CredentialError.MISMATCH, "Current credential is incorrect")
            }
            settingsRepository.saveCredential(newMode, hasher.hash(newSecret))
            events.log(
                SecurityEventType.CREDENTIAL_CHANGED,
                SecuritySeverity.INFO,
                "Unlock credential changed (${newMode.name.lowercase()})",
            )
            Unit
        }
    }

    suspend fun setAuthModeIfCredentialMatches(currentSecret: String, newMode: AuthMode): Result<Unit> =
        withContext(dispatchers.default) {
            runCatching {
                if (verify(currentSecret) !is AuthOutcome.Success) {
                    throw CredentialException(CredentialError.MISMATCH, "Current credential is incorrect")
                }
                settingsRepository.setAuthMode(newMode)
                Unit
            }
        }

    /**
     * Recovers access with the single-use recovery code and rotates it.
     * @return the new recovery code to show to the user exactly once.
     */
    suspend fun recover(
        recoveryCode: String,
        newMode: AuthMode,
        newSecret: String,
    ): Result<String> = withContext(dispatchers.default) {
        runCatching {
            val now = timeProvider.nowMillis()
            val settings = settingsRepository.current()
            if (now < settings.lockoutUntilMillis) {
                throw CredentialException(
                    CredentialError.EMPTY,
                    "Authentication is paused for ${(settings.lockoutUntilMillis - now) / 1000}s",
                )
            }
            val storedRecovery = settingsRepository.recoveryHash()
                ?: throw CredentialException(CredentialError.EMPTY, "No recovery code is configured")
            val matches = hasher.verify(normaliseRecoveryCode(recoveryCode), storedRecovery)
            if (!matches) {
                val attempts = settings.failedAttempts + 1
                val lockoutUntil = LockoutPolicy.lockoutUntil(now, attempts)
                settingsRepository.recordFailedAttempt(attempts, lockoutUntil)
                events.log(
                    SecurityEventType.AUTH_FAILURE,
                    SecuritySeverity.WARNING,
                    "Invalid recovery code entered",
                )
                throw CredentialException(CredentialError.MISMATCH, "Recovery code is incorrect")
            }
            validateOrThrow(newMode, newSecret)
            settingsRepository.saveCredential(newMode, hasher.hash(newSecret))
            val newRecovery = generateRecoveryCode()
            settingsRepository.saveRecoveryHash(hasher.hash(normaliseRecoveryCode(newRecovery)))
            events.log(
                SecurityEventType.CREDENTIAL_RECOVERED,
                SecuritySeverity.CRITICAL,
                "Access recovered with a recovery code",
            )
            newRecovery
        }
    }

    /** Destroys all credentials. Used only by the explicit "reset NexLock" flow. */
    suspend fun wipeCredentials() = withContext(dispatchers.default) {
        settingsRepository.clearCredential()
        settingsRepository.clearRecovery()
        settingsRepository.setBiometricEnabled(false)
        events.log(
            SecurityEventType.CREDENTIAL_CHANGED,
            SecuritySeverity.CRITICAL,
            "All authentication credentials were removed",
        )
    }

    suspend fun lockoutRemainingMillis(): Long {
        val now = timeProvider.nowMillis()
        val until = settingsRepository.current().lockoutUntilMillis
        return (until - now).coerceAtLeast(0L)
    }

    private fun validateOrThrow(mode: AuthMode, secret: String) {
        val validation = CredentialValidator.validate(mode, secret)
        if (!validation.valid) {
            throw CredentialException(validation.error ?: CredentialError.EMPTY)
        }
    }

    private fun generateRecoveryCode(): String {
        val groups = (0 until RECOVERY_GROUPS).map {
            (0 until RECOVERY_GROUP_LENGTH)
                .map { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] }
                .joinToString("")
        }
        return groups.joinToString("-")
    }

    fun normaliseRecoveryCode(code: String): String =
        code.uppercase().filter { it.isLetterOrDigit() }

    companion object {
        private const val RECOVERY_GROUPS = 4
        private const val RECOVERY_GROUP_LENGTH = 4
        // 32 unambiguous characters: 80 bits of entropy over 16 symbols.
        private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
