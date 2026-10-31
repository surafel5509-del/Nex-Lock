package com.nexlock.common.model

/** How the user proves identity to unlock a protected app. */
enum class AuthMode {
    PIN,
    PATTERN,
    PASSWORD;

    companion object {
        fun fromStorage(value: String?): AuthMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PIN
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK;
    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    AMHARIC("am"),
    SPANISH("es");

    companion object {
        fun fromStorage(value: String?): AppLanguage =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ENGLISH
    }
}

/** Aggregate device risk after root / debugger / emulator / signature inspection. */
enum class RiskLevel { LOW, MEDIUM, HIGH }

enum class SecuritySeverity { INFO, WARNING, CRITICAL }

enum class SecurityEventType {
    PROTECTION_ENABLED,
    PROTECTION_DISABLED,
    APP_LOCKED,
    APP_UNLOCKED,
    AUTH_SUCCESS,
    AUTH_FAILURE,
    LOCKOUT_TRIGGERED,
    INTRUDER_CAPTURED,
    CREDENTIAL_CHANGED,
    CREDENTIAL_RECOVERED,
    BIOMETRIC_ENABLED,
    BIOMETRIC_DISABLED,
    VAULT_IMPORT,
    VAULT_EXPORT,
    VAULT_DELETE,
    AUTOMATION_APPLIED,
    TEMPORARY_UNLOCK,
    BACKUP_CREATED,
    BACKUP_RESTORED,
    RISK_DETECTED;

    companion object {
        fun fromStorage(value: String?): SecurityEventType =
            entries.firstOrNull { it.name == value } ?: RISK_DETECTED
    }
}

enum class VaultKind { PHOTO, VIDEO, DOCUMENT;
    companion object {
        fun fromStorage(value: String?): VaultKind =
            entries.firstOrNull { it.name == value } ?: DOCUMENT
    }
}

/** Coarse bucket used for the application picker filters. */
enum class AppCategory { SOCIAL, MEDIA, FINANCE, COMMUNICATION, PRODUCTIVITY, GAMES, SYSTEM, OTHER;
    companion object {
        fun fromStorage(value: String?): AppCategory =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}

enum class SecurityLevel { AT_RISK, FAIR, STRONG, OPTIMAL }

/** Where a security recommendation should navigate to. */
enum class RecommendationTarget {
    APPS,
    AUTHENTICATION,
    BIOMETRIC,
    ACCESSIBILITY,
    USAGE_ACCESS,
    NOTIFICATIONS,
    INTRUDER,
    SECURITY_LOG,
    AUTOMATION
}

data class SecurityRecommendation(
    val id: String,
    val title: String,
    val description: String,
    val target: RecommendationTarget,
    val severity: SecuritySeverity,
)

data class ScoreFactor(
    val id: String,
    val label: String,
    val points: Int,
    val satisfied: Boolean,
)

data class SecurityScore(
    val score: Int,
    val level: SecurityLevel,
    val factors: List<ScoreFactor>,
)

/** Immutable snapshot of what the UI needs to render a risk banner. */
data class RiskReport(
    val level: RiskLevel,
    val signals: List<String>,
    val isRooted: Boolean,
    val isDebuggable: Boolean,
    val isEmulator: Boolean,
    val isSignatureValid: Boolean,
) {
    val hasSignals: Boolean get() = signals.isNotEmpty()
}
