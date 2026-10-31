package com.nexlock.common.policy

import com.nexlock.common.model.RecommendationTarget
import com.nexlock.common.model.RiskLevel
import com.nexlock.common.model.SecurityLevel
import com.nexlock.common.model.SecurityRecommendation
import com.nexlock.common.model.SecurityScore
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.model.ScoreFactor

data class SecurityPostureInput(
    val protectionEnabled: Boolean = false,
    val credentialConfigured: Boolean = false,
    val biometricEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val intruderDetectionEnabled: Boolean = false,
    val screenshotsBlocked: Boolean = true,
    val lockedAppCount: Int = 0,
    val riskLevel: RiskLevel = RiskLevel.LOW,
)

/**
 * Weighted, explainable security score (0..100). Every factor carries its own points so the UI can
 * show the user exactly why the score is what it is.
 */
object SecurityScoreCalculator {

    fun calculate(input: SecurityPostureInput): SecurityScore {
        val factors = buildList {
            add(ScoreFactor("protection", "Protection service active", 25, input.protectionEnabled))
            add(ScoreFactor("credential", "Lock credential configured", 15, input.credentialConfigured))
            add(ScoreFactor("biometric", "Biometric unlock enabled", 10, input.biometricEnabled))
            add(ScoreFactor("realtime", "Real-time monitoring granted", 15, input.accessibilityEnabled))
            add(ScoreFactor("usage", "Usage-access fallback granted", 5, input.usageAccessGranted))
            add(ScoreFactor("apps", "At least one app protected", 10, input.lockedAppCount > 0))
            add(ScoreFactor("intruder", "Intruder detection enabled", 5, input.intruderDetectionEnabled))
            add(ScoreFactor("screens", "Screenshot protection on", 5, input.screenshotsBlocked))
            add(ScoreFactor("notifications", "Security alerts enabled", 5, input.notificationsGranted))
            add(
                ScoreFactor(
                    "risk",
                    "No tampering signals detected",
                    5,
                    input.riskLevel == RiskLevel.LOW,
                )
            )
        }
        val score = factors.filter { it.satisfied }.sumOf { it.points }.coerceIn(0, 100)
        return SecurityScore(score = score, level = levelFor(score, input.riskLevel), factors = factors)
    }

    private fun levelFor(score: Int, risk: RiskLevel): SecurityLevel = when {
        score >= 85 && risk == RiskLevel.LOW -> SecurityLevel.OPTIMAL
        score >= 60 -> SecurityLevel.STRONG
        score >= 35 -> SecurityLevel.FAIR
        else -> SecurityLevel.AT_RISK
    }

    /**
     * Recommendations, ordered by impact. Empty when the posture is already optimal, which lets the
     * UI render a genuine "all good" state instead of filler content.
     */
    fun recommendations(input: SecurityPostureInput): List<SecurityRecommendation> = buildList {
        if (!input.protectionEnabled) {
            add(SecurityRecommendation("rec_protection", "Turn on protection", "Start the NexLock monitoring service so locked apps stay locked.", RecommendationTarget.ACCESSIBILITY, SecuritySeverity.CRITICAL))
        }
        if (input.lockedAppCount == 0) {
            add(SecurityRecommendation("rec_apps", "Protect your first app", "Choose the apps that should require authentication.", RecommendationTarget.APPS, SecuritySeverity.WARNING))
        }
        if (!input.accessibilityEnabled) {
            add(SecurityRecommendation("rec_accessibility", "Enable real-time monitoring", "Grant the accessibility service so protected apps are intercepted instantly.", RecommendationTarget.ACCESSIBILITY, SecuritySeverity.WARNING))
        }
        if (!input.usageAccessGranted) {
            add(SecurityRecommendation("rec_usage", "Enable the fallback monitor", "Usage access keeps protection working when the accessibility service is off.", RecommendationTarget.USAGE_ACCESS, SecuritySeverity.INFO))
        }
        if (!input.biometricEnabled) {
            add(SecurityRecommendation("rec_biometric", "Add biometric unlock", "Unlock faster with fingerprint or face while keeping your PIN fallback.", RecommendationTarget.BIOMETRIC, SecuritySeverity.INFO))
        }
        if (!input.intruderDetectionEnabled) {
            add(SecurityRecommendation("rec_intruder", "Enable intruder detection", "Log failed attempts locally and optionally capture a photo after repeated failures.", RecommendationTarget.INTRUDER, SecuritySeverity.INFO))
        }
        if (!input.screenshotsBlocked) {
            add(SecurityRecommendation("rec_screenshots", "Block screenshots", "Prevent protected screens from appearing in the recent-apps thumbnail.", RecommendationTarget.AUTHENTICATION, SecuritySeverity.INFO))
        }
        if (input.riskLevel == RiskLevel.HIGH) {
            add(SecurityRecommendation("rec_risk", "Review tampering signals", "A rooted or debuggable device can weaken app-level protection. Review the security log.", RecommendationTarget.SECURITY_LOG, SecuritySeverity.WARNING))
        }
    }
}
