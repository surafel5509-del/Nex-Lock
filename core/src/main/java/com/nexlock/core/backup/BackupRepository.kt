package com.nexlock.core.backup

import android.content.Context
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.common.util.NexLog
import com.nexlock.common.util.TimeProvider
import com.nexlock.core.apps.LockedApp
import com.nexlock.core.apps.LockedAppsRepository
import com.nexlock.core.automation.AutomationRepository
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.core.vault.VaultRepository
import com.nexlock.security.backup.BackupCodec
import com.nexlock.security.backup.BackupPayload
import com.nexlock.security.backup.LocationRuleBackup
import com.nexlock.security.backup.LockedAppBackup
import com.nexlock.security.backup.NoteBackup
import com.nexlock.security.backup.ScheduleBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class RestoreSummary(
    val lockedApps: Int,
    val notes: Int,
    val schedules: Int,
    val trustedNetworks: Int,
    val locationRules: Int,
)

/**
 * Encrypted, portable backup of configuration and private text data.
 *
 * Vault media is intentionally **not** included: it can be many gigabytes and is bound to this
 * device's Keystore key. The backup file itself is AES-256-GCM encrypted with a passphrase the
 * user chooses, and contains no credential hashes.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val lockedAppsRepository: LockedAppsRepository,
    private val vaultRepository: VaultRepository,
    private val automationRepository: AutomationRepository,
    private val events: SecurityEventRepository,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun createBackup(passphrase: CharArray): Result<ByteArray> = withContext(dispatchers.io) {
        runCatching {
            require(passphrase.size >= MIN_PASSPHRASE) {
                "Backup passphrase must be at least $MIN_PASSPHRASE characters"
            }
            val settings = settingsRepository.exportPreferenceMap()
            val lockedApps = lockedAppsRepository.lockedList().map {
                LockedAppBackup(it.packageName, it.appName, it.category.name, it.lockedAt)
            }
            val notes = vaultRepository.observeNotes().first().map {
                NoteBackup(it.title, it.content, it.createdAt, it.updatedAt)
            }
            val schedules = automationRepository.schedules.first().map {
                ScheduleBackup(it.label, it.startMinute, it.endMinute, it.daysMask, it.enabled)
            }
            val trustedNetworks = automationRepository.trustedNetworks.first().map { it.ssid }
            val locations = automationRepository.locationRules.first().map {
                LocationRuleBackup(it.label, it.latitude, it.longitude, it.radiusMeters, it.enabled)
            }

            val payload = BackupPayload(
                schemaVersion = BackupPayload.CURRENT_SCHEMA,
                createdAt = timeProvider.nowMillis(),
                appVersion = appVersion(),
                settings = settings,
                lockedApps = lockedApps,
                notes = notes,
                schedules = schedules,
                trustedNetworks = trustedNetworks,
                locationRules = locations,
            )
            events.log(
                SecurityEventType.BACKUP_CREATED,
                SecuritySeverity.INFO,
                "Encrypted backup created (${lockedApps.size} apps, ${notes.size} notes)",
            )
            BackupCodec.encode(payload, passphrase)
        }.onFailure { NexLog.w(TAG, "Backup creation failed") }
    }

    suspend fun restoreBackup(bytes: ByteArray, passphrase: CharArray): Result<RestoreSummary> =
        withContext(dispatchers.io) {
            runCatching {
                val payload = BackupCodec.decode(bytes, passphrase).getOrThrow()
                settingsRepository.importPreferenceMap(payload.settings)

                lockedAppsRepository.restore(
                    payload.lockedApps.map {
                        LockedApp(
                            packageName = it.packageName,
                            appName = it.appName.ifBlank { it.packageName },
                            isLocked = true,
                            category = com.nexlock.common.model.AppCategory.fromStorage(it.category),
                            lockedAt = it.lockedAt,
                        )
                    }
                )
                payload.notes.forEach { note ->
                    vaultRepository.saveNote(null, note.title, note.content)
                }
                payload.schedules.forEach { schedule ->
                    automationRepository.addSchedule(
                        schedule.label, schedule.startMinute, schedule.endMinute, schedule.daysMask,
                    )
                }
                payload.trustedNetworks.forEach { automationRepository.addTrustedNetwork(it) }
                payload.locationRules.forEach {
                    automationRepository.addLocationRule(
                        it.label, it.latitude, it.longitude, it.radiusMeters,
                    )
                }
                automationRepository.invalidateCache()

                val summary = RestoreSummary(
                    lockedApps = payload.lockedApps.size,
                    notes = payload.notes.size,
                    schedules = payload.schedules.size,
                    trustedNetworks = payload.trustedNetworks.size,
                    locationRules = payload.locationRules.size,
                )
                events.log(
                    SecurityEventType.BACKUP_RESTORED,
                    SecuritySeverity.INFO,
                    "Encrypted backup restored (${summary.lockedApps} apps, ${summary.notes} notes)",
                )
                summary
            }.onFailure { NexLog.w(TAG, "Backup restore failed") }
        }

    private fun appVersion(): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    companion object {
        private const val TAG = "Backup"
        private const val MIN_PASSPHRASE = 8
    }
}
