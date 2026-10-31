package com.nexlock.core.auth

import com.nexlock.common.model.AuthMode
import com.nexlock.common.policy.CredentialError

data class AuthState(
    val configured: Boolean,
    val recoveryConfigured: Boolean,
    val mode: AuthMode,
    val biometricEnabled: Boolean,
    val failedAttempts: Int,
    val lockoutUntilMillis: Long,
) {
    val lockedOut: Boolean get() = lockoutUntilMillis > System.currentTimeMillis()
}

sealed interface AuthOutcome {
    data object Success : AuthOutcome

    /** Wrong credential, no cool-down yet. */
    data class Invalid(val attempts: Int, val attemptsBeforeLockout: Int) : AuthOutcome

    /** Authentication suspended; [remainingMillis] ticks down to zero. */
    data class LockedOut(val remainingMillis: Long, val attempts: Int) : AuthOutcome

    /** No credential has been configured yet (fresh install / after a reset). */
    data object NotConfigured : AuthOutcome

    data class Error(val message: String) : AuthOutcome
}

/** Thrown internally for validation problems, converted to `Result.failure` at the boundary. */
class CredentialException(val error: CredentialError, message: String? = null) :
    IllegalArgumentException(message ?: error.name)
