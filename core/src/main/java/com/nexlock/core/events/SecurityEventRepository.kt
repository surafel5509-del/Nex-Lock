package com.nexlock.core.events

import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.common.util.TimeProvider
import com.nexlock.core.notifications.NexNotifier
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.database.dao.SecurityEventDao
import com.nexlock.database.entity.SecurityEventEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Append-only local security journal.
 *
 * Messages must never contain credentials, decrypted file names or vault content. Entries are
 * pruned according to the retention setting, and the user can wipe the journal at any time.
 */
@Singleton
class SecurityEventRepository @Inject constructor(
    private val dao: SecurityEventDao,
    private val notifier: NexNotifier,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    fun observeRecent(limit: Int = 40): Flow<List<SecurityEvent>> =
        dao.observeRecent(limit).map { events -> events.map { it.toDomain() } }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun log(
        type: SecurityEventType,
        severity: SecuritySeverity,
        message: String,
        packageName: String? = null,
    ): Long = withContext(dispatchers.io) {
        val id = dao.insert(
            SecurityEventEntity(
                type = type,
                severity = severity,
                message = message,
                packageName = packageName,
                timestamp = timeProvider.nowMillis(),
            )
        )
        notifier.notifyForEvent(type, severity)
        id
    }

    suspend fun pruneExpired() = withContext(dispatchers.io) {
        val retentionDays = settingsRepository.current().securityLogRetentionDays
        val cutoff = timeProvider.nowMillis() - retentionDays * DAY_MILLIS
        dao.deleteOlderThan(cutoff)
    }

    suspend fun clearAll() = withContext(dispatchers.io) { dao.clear() }

    private fun SecurityEventEntity.toDomain() = SecurityEvent(
        id = id,
        type = type,
        severity = severity,
        message = message,
        packageName = packageName,
        timestamp = timestamp,
    )

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
