package com.nexlock.common.policy

import com.nexlock.common.model.AuthMode

enum class CredentialError {
    EMPTY,
    TOO_SHORT,
    TOO_LONG,
    NON_NUMERIC,
    WEAK_PASSWORD,
    PATTERN_TOO_SHORT,
    PATTERN_INVALID,
    MISMATCH,
}

data class CredentialValidation(val valid: Boolean, val error: CredentialError? = null) {
    companion object {
        val Valid = CredentialValidation(true)
        fun invalid(error: CredentialError) = CredentialValidation(false, error)
    }
}

/**
 * Validates user supplied secrets before they are ever hashed or persisted.
 * Pattern secrets are encoded as the 0..8 cell indices joined by '-', e.g. "0-1-4-7".
 */
object CredentialValidator {

    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8
    const val MIN_PASSWORD_LENGTH = 8
    const val MIN_PATTERN_CELLS = 4

    fun validate(mode: AuthMode, secret: String): CredentialValidation = when (mode) {
        AuthMode.PIN -> validatePin(secret)
        AuthMode.PATTERN -> validatePattern(secret)
        AuthMode.PASSWORD -> validatePassword(secret)
    }

    fun validatePin(pin: String): CredentialValidation {
        if (pin.isEmpty()) return CredentialValidation.invalid(CredentialError.EMPTY)
        if (!pin.all { it.isDigit() }) return CredentialValidation.invalid(CredentialError.NON_NUMERIC)
        if (pin.length < MIN_PIN_LENGTH) return CredentialValidation.invalid(CredentialError.TOO_SHORT)
        if (pin.length > MAX_PIN_LENGTH) return CredentialValidation.invalid(CredentialError.TOO_LONG)
        return CredentialValidation.Valid
    }

    fun validatePassword(password: String): CredentialValidation {
        if (password.isBlank()) return CredentialValidation.invalid(CredentialError.EMPTY)
        if (password.length < MIN_PASSWORD_LENGTH) return CredentialValidation.invalid(CredentialError.TOO_SHORT)
        if (password.length > 128) return CredentialValidation.invalid(CredentialError.TOO_LONG)
        val hasLetter = password.any { it.isLetter() }
        val hasDigitOrSymbol = password.any { it.isDigit() || !it.isLetterOrDigit() }
        if (!hasLetter || !hasDigitOrSymbol) return CredentialValidation.invalid(CredentialError.WEAK_PASSWORD)
        return CredentialValidation.Valid
    }

    /** @param encoded pattern path such as "0-1-4-7". */
    fun validatePattern(encoded: String): CredentialValidation {
        if (encoded.isBlank()) return CredentialValidation.invalid(CredentialError.EMPTY)
        val cells = encoded.split('-').mapNotNull { it.trim().toIntOrNull() }
        if (cells.size != encoded.split('-').size) return CredentialValidation.invalid(CredentialError.PATTERN_INVALID)
        if (cells.any { it !in 0..8 }) return CredentialValidation.invalid(CredentialError.PATTERN_INVALID)
        if (cells.distinct().size != cells.size) return CredentialValidation.invalid(CredentialError.PATTERN_INVALID)
        if (cells.size < MIN_PATTERN_CELLS) return CredentialValidation.invalid(CredentialError.PATTERN_TOO_SHORT)
        return CredentialValidation.Valid
    }

    /** Length of the user-visible secret, used for PIN progress dots only. */
    fun displayLength(mode: AuthMode, secret: String): Int = when (mode) {
        AuthMode.PATTERN -> if (secret.isBlank()) 0 else secret.split('-').size
        else -> secret.length
    }
}
