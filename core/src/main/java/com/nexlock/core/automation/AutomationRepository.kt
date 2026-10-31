package com.nexlock.core.automation

import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.policy.AutomationContext
import com.nexlock.common.policy.AutomationDecision
import com.nexlock.common.policy.AutomationEvaluator
import com.nexlock.common.policy.ScheduleCalculator
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.common.util.TimeProvider
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.database.dao.AutomationDao
import com.nexlock.database.entity.LockScheduleEntity
import com.nexlock.database.entity.LocationRuleEntity
import com.nexlock.database.entity.TrustedWifiEntity
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Schedules, trusted networks, trusted locations and temporary unlocks.
 *
 * All evaluation happens against the current wall clock and current device context; results are
 * cached for a few seconds because the monitor service asks on every foreground change.
 */
@Singleton
class AutomationRepository @Inject constructor(
    private val dao: AutomationDao,
    private val settingsRepository: SettingsRepository,
    private val deviceContext: DeviceContextProvider,
    private val events: SecurityEventRepository,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    @Volatile
    private var cachedSnapshot: AutomationSnapshot? = null

    @Volatile
    private var cachedAtMillis: Long = 0L

    val schedules: Flow<List<LockSchedule>> = dao.observeSchedules().map { list -> list.map { it.toDomain() } }
    val trustedNetworks: Flow<List<TrustedNetwork>> = dao.observeTrustedWifi().map { list -> list.map { it.toDomain() } }
    val locationRules: Flow<List<LocationRule>> = dao.observeLocations().map { list -> list.map { it.toDomain() } }

    fun observeScheduleWindows(): Flow<List<LockSchedule>> = schedules

    // ------------------------------------------------------------------ schedules

    suspend fun addSchedule(label: String, startMinute: Int, endMinute: Int, daysMask: Int): Long =
        withContext(dispatchers.io) {
            invalidateCache()
            dao.insertSchedule(
                LockScheduleEntity(
                    label = label.trim().ifBlank { "Schedule" },
                    startMinute = startMinute.coerceIn(0, 1439),
                    endMinute = endMinute.coerceIn(0, 1439),
                    daysMask = daysMask and ScheduleCalculator.ALL_DAYS_MASK,
                )
            )
        }

    suspend fun setScheduleEnabled(id: Long, enabled: Boolean) = withContext(dispatchers.io) {
        invalidateCache()
        dao.setScheduleEnabled(id, enabled)
    }

    suspend fun deleteSchedule(id: Long) = withContext(dispatchers.io) {
        invalidateCache()
        dao.deleteSchedule(id)
    }

    // ------------------------------------------------------------------ trusted networks

    suspend fun addTrustedNetwork(ssid: String) = withContext(dispatchers.io) {
        val clean = ssid.removeSurrounding("\"").trim()
        if (clean.isNotEmpty()) {
            invalidateCache()
            dao.insertTrustedWifi(TrustedWifiEntity(ssid = clean))
        }
    }

    suspend fun setTrustedNetworkEnabled(ssid: String, enabled: Boolean) = withContext(dispatchers.io) {
        invalidateCache()
        dao.setTrustedWifiEnabled(ssid, enabled)
    }

    suspend fun removeTrustedNetwork(ssid: String) = withContext(dispatchers.io) {
        invalidateCache()
        dao.deleteTrustedWifi(ssid)
    }

    // ------------------------------------------------------------------ location rules

    suspend fun addLocationFromCurrent(label: String, radiusMeters: Float): Result<LocationRule> =
        withContext(dispatchers.io) {
            val location = deviceContext.lastKnownLocation()
                ?: return@withContext Result.failure(IllegalStateException("No location available yet"))
            val rule = LocationRuleEntity(
                label = label.trim().ifBlank { "Trusted place" },
                latitude = location.latitude,
                longitude = location.longitude,
                radiusMeters = radiusMeters.coerceIn(50f, 5000f),
            )
            val id = dao.insertLocation(rule)
            invalidateCache()
            Result.success(rule.copy(id = id).toDomain())
        }

    suspend fun addLocationRule(label: String, latitude: Double, longitude: Double, radiusMeters: Float): Long =
        withContext(dispatchers.io) {
            invalidateCache()
            dao.insertLocation(
                LocationRuleEntity(
                    label = label.trim().ifBlank { "Trusted place" },
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters.coerceIn(50f, 5000f),
                )
            )
        }

    suspend fun setLocationRuleEnabled(id: Long, enabled: Boolean) = withContext(dispatchers.io) {
        invalidateCache()
        dao.setLocationEnabled(id, enabled)
    }

    suspend fun deleteLocationRule(id: Long) = withContext(dispatchers.io) {
        invalidateCache()
        dao.deleteLocation(id)
    }

    // ------------------------------------------------------------------ context

    suspend fun currentSsid(): String? = withContext(dispatchers.io) { deviceContext.currentSsid() }

    suspend fun hasLocationPermission(): Boolean = deviceContext.hasLocationPermission()

    suspend fun evaluateNow(forceRefresh: Boolean = false): AutomationSnapshot = withContext(dispatchers.io) {
        val now = timeProvider.nowMillis()
        val cached = cachedSnapshot
        if (!forceRefresh && cached != null && now - cachedAtMillis < CACHE_TTL_MILLIS) {
            return@withContext cached
        }

        val settings = settingsRepository.current()
        if (!settings.automationEnabled) {
            return@withContext AutomationSnapshot(scheduleEnabled = settings.scheduleEnabled).also {
                cachedSnapshot = it
                cachedAtMillis = now
            }
        }

        val windows = dao.schedules().map { it.toDomain() }
        val activeWindow = if (settings.scheduleEnabled) {
            ScheduleCalculator.activeWindow(windows.map { it.toWindow() }, LocalDateTime.now())
        } else {
            null
        }

        val ssid = if (settings.wifiAutomationEnabled) deviceContext.currentSsid() else null
        val trustedSsids = if (settings.wifiAutomationEnabled) {
            dao.trustedWifi().filter { it.enabled }.map { it.ssid }
        } else {
            emptyList()
        }
        val onTrustedNetwork = ssid != null && trustedSsids.contains(ssid)

        var trustedLocationLabel: String? = null
        if (settings.locationAutomationEnabled && deviceContext.hasLocationPermission()) {
            deviceContext.lastKnownLocation()?.let { location ->
                dao.enabledLocations().firstOrNull { rule ->
                    deviceContext.distanceMeters(
                        location.latitude, location.longitude, rule.latitude, rule.longitude,
                    ) <= rule.radiusMeters
                }?.let { trustedLocationLabel = it.label }
            }
        }

        val temporaryUnlockActive = settings.temporaryUnlockUntilMillis > now
        val decision = AutomationEvaluator.evaluate(
            AutomationContext(
                automationEnabled = true,
                insideLockSchedule = activeWindow != null,
                onTrustedNetwork = onTrustedNetwork,
                insideTrustedLocation = trustedLocationLabel != null,
                temporaryUnlockActive = temporaryUnlockActive,
            )
        )

        val snapshot = AutomationSnapshot(
            decision = decision,
            scheduleEnabled = settings.scheduleEnabled,
            activeWindowLabel = activeWindow?.label,
            connectedSsid = ssid,
            trustedNetworkMatched = onTrustedNetwork,
            trustedLocationLabel = trustedLocationLabel,
            temporaryUnlockActive = temporaryUnlockActive,
            locationPermissionGranted = deviceContext.hasLocationPermission(),
        )
        cachedSnapshot = snapshot
        cachedAtMillis = now

        if (decision is AutomationDecision.ForceLock && cached?.decision !is AutomationDecision.ForceLock) {
            events.log(
                SecurityEventType.AUTOMATION_APPLIED,
                SecuritySeverity.INFO,
                "Scheduled protection window active (${activeWindow?.label ?: "schedule"})",
            )
        }
        snapshot
    }

    suspend fun startTemporaryUnlock(minutes: Int) = withContext(dispatchers.io) {
        val until = timeProvider.nowMillis() + minutes.coerceIn(1, 120) * 60_000L
        settingsRepository.setTemporaryUnlockUntil(until)
        invalidateCache()
        events.log(
            SecurityEventType.TEMPORARY_UNLOCK,
            SecuritySeverity.WARNING,
            "Temporary unlock enabled for $minutes minute(s)",
        )
    }

    suspend fun endTemporaryUnlock() = withContext(dispatchers.io) {
        settingsRepository.setTemporaryUnlockUntil(0L)
        invalidateCache()
    }

    fun invalidateCache() {
        cachedSnapshot = null
        cachedAtMillis = 0L
    }

    private fun LockScheduleEntity.toDomain() = LockSchedule(id, label, startMinute, endMinute, daysMask, enabled)
    private fun TrustedWifiEntity.toDomain() = TrustedNetwork(ssid, enabled, addedAt)
    private fun LocationRuleEntity.toDomain() = LocationRule(id, label, latitude, longitude, radiusMeters, enabled)

    companion object {
        private const val CACHE_TTL_MILLIS = 8_000L
    }
}
