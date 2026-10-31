package com.nexlock.core.security

import com.nexlock.common.model.RiskReport
import com.nexlock.common.model.SecurityRecommendation
import com.nexlock.common.model.SecurityScore
import com.nexlock.common.policy.SecurityPostureInput
import com.nexlock.common.policy.SecurityScoreCalculator
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.common.util.NexLog
import com.nexlock.core.apps.LockedAppsRepository
import com.nexlock.core.automation.AutomationRepository
import com.nexlock.core.automation.AutomationSnapshot
import com.nexlock.core.permissions.PermissionStatus
import com.nexlock.core.permissions.PermissionStatusProvider
import com.nexlock.core.settings.AppSettings
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.security.crypto.CryptoManager
import com.nexlock.security.risk.RiskAssessor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Everything the dashboard and the security screen need, computed once per refresh. */
data class SecurityPosture(
    val settings: AppSettings,
    val score: SecurityScore,
    val risk: RiskReport,
    val permissions: PermissionStatus,
    val automation: AutomationSnapshot,
    val lockedAppsCount: Int,
    val recommendations: List<SecurityRecommendation>,
    val hardwareBackedKey: Boolean,
)

@Singleton
class SecurityPostureRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val lockedAppsRepository: LockedAppsRepository,
    private val permissionStatusProvider: PermissionStatusProvider,
    private val automationRepository: AutomationRepository,
    private val riskAssessor: RiskAssessor,
    private val cryptoManager: CryptoManager,
    private val dispatchers: DispatcherProvider,
) {

    private val refreshTrigger = MutableStateFlow(0L)

    @Volatile
    private var cachedHardwareBacked: Boolean? = null

    val posture: Flow<SecurityPosture> = combine(
        settingsRepository.settings,
        lockedAppsRepository.lockedCount,
        refreshTrigger,
    ) { settings, lockedCount, _ -> settings to lockedCount }
        .map { (settings, lockedCount) -> buildPosture(settings, lockedCount) }
        .flowOn(dispatchers.io)

    /** Bump when permissions or device state may have changed (screen resume, settings return). */
    fun refresh() {
        refreshTrigger.value = refreshTrigger.value + 1
    }

    fun invalidateHardwareCache() {
        cachedHardwareBacked = null
    }

    private suspend fun buildPosture(settings: AppSettings, lockedCount: Int): SecurityPosture {
        val permissions = permissionStatusProvider.status()
        val risk = riskAssessor.assess()
        val automation = automationRepository.evaluateNow()
        val input = SecurityPostureInput(
            protectionEnabled = settings.protectionEnabled,
            credentialConfigured = settings.credentialConfigured,
            biometricEnabled = settings.biometricEnabled,
            accessibilityEnabled = permissions.accessibilityServiceEnabled,
            usageAccessGranted = permissions.usageAccessGranted,
            notificationsGranted = permissions.notificationsGranted,
            intruderDetectionEnabled = settings.intruderDetectionEnabled,
            screenshotsBlocked = settings.screenshotsBlocked,
            lockedAppCount = lockedCount,
            riskLevel = risk.level,
        )
        val hardware = cachedHardwareBacked ?: runCatching { cryptoManager.isHardwareBacked() }
            .onFailure { NexLog.w(TAG, "Unable to query Keystore security level") }
            .getOrDefault(false)
            .also { cachedHardwareBacked = it }

        return SecurityPosture(
            settings = settings,
            score = SecurityScoreCalculator.calculate(input),
            risk = risk,
            permissions = permissions,
            automation = automation,
            lockedAppsCount = lockedCount,
            recommendations = SecurityScoreCalculator.recommendations(input),
            hardwareBackedKey = hardware,
        )
    }

    companion object {
        private const val TAG = "SecurityPosture"
    }
}
