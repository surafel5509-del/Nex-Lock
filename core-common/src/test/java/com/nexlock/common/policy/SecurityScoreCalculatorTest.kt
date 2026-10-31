package com.nexlock.common.policy

import com.nexlock.common.model.RiskLevel
import com.nexlock.common.model.SecurityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityScoreCalculatorTest {

    @Test
    fun `perfect posture scores 100 and is optimal`() {
        val score = SecurityScoreCalculator.calculate(
            SecurityPostureInput(
                protectionEnabled = true,
                credentialConfigured = true,
                biometricEnabled = true,
                accessibilityEnabled = true,
                usageAccessGranted = true,
                notificationsGranted = true,
                intruderDetectionEnabled = true,
                screenshotsBlocked = true,
                lockedAppCount = 4,
                riskLevel = RiskLevel.LOW,
            )
        )
        assertEquals(100, score.score)
        assertEquals(SecurityLevel.OPTIMAL, score.level)
        assertTrue(SecurityScoreCalculator.recommendations(SecurityPostureInput(protectionEnabled = true, credentialConfigured = true, biometricEnabled = true, accessibilityEnabled = true, usageAccessGranted = true, notificationsGranted = true, intruderDetectionEnabled = true, lockedAppCount = 1)).isEmpty())
    }

    @Test
    fun `empty posture is at risk and produces recommendations`() {
        val input = SecurityPostureInput()
        val score = SecurityScoreCalculator.calculate(input)
        assertEquals(SecurityLevel.AT_RISK, score.level)
        assertTrue(score.score < 35)
        val recommendations = SecurityScoreCalculator.recommendations(input)
        assertTrue(recommendations.size >= 5)
        assertEquals(recommendations.map { it.id }.distinct().size, recommendations.size)
    }

    @Test
    fun `factors are individually explainable`() {
        val score = SecurityScoreCalculator.calculate(SecurityPostureInput(protectionEnabled = true))
        val protection = score.factors.first { it.id == "protection" }
        assertEquals(25, protection.points)
        assertTrue(protection.satisfied)
    }
}
