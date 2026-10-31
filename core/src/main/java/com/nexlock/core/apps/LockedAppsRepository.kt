package com.nexlock.core.apps

import com.nexlock.common.model.AppCategory
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.common.util.TimeProvider
import com.nexlock.core.events.SecurityEventRepository
import com.nexlock.database.dao.LockedAppDao
import com.nexlock.database.entity.LockedAppEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class LockedApp(
    val packageName: String,
    val appName: String,
    val isLocked: Boolean,
    val category: AppCategory,
    val lockedAt: Long,
)

/** The protected-app registry. Every write also produces a local security journal entry. */
@Singleton
class LockedAppsRepository @Inject constructor(
    private val dao: LockedAppDao,
    private val installedAppsProvider: InstalledAppsProvider,
    private val events: SecurityEventRepository,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    val lockedApps: Flow<List<LockedApp>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val lockedOnly: Flow<List<LockedApp>> = dao.observeLocked().map { list -> list.map { it.toDomain() } }

    val lockedPackages: Flow<Set<String>> = dao.observeLockedPackages().map { it.toSet() }

    val lockedCount: Flow<Int> = dao.observeLockedCount()

    suspend fun installedApps(): List<InstalledApp> = installedAppsProvider.launcherApps()

    suspend fun setLocked(app: InstalledApp, locked: Boolean) = setLocked(
        packageName = app.packageName,
        appName = app.label,
        category = app.category,
        locked = locked,
    )

    suspend fun setLocked(
        packageName: String,
        appName: String,
        category: AppCategory,
        locked: Boolean,
    ) = withContext(dispatchers.io) {
        val existing = dao.find(packageName)
        dao.upsert(
            LockedAppEntity(
                packageName = packageName,
                appName = appName,
                isLocked = locked,
                category = category,
                lockedAt = existing?.lockedAt ?: timeProvider.nowMillis(),
            )
        )
        events.log(
            if (locked) SecurityEventType.APP_LOCKED else SecurityEventType.APP_UNLOCKED,
            SecuritySeverity.INFO,
            if (locked) "Protection enabled for $appName" else "Protection disabled for $appName",
            packageName,
        )
    }

    suspend fun lockMany(apps: List<InstalledApp>) = withContext(dispatchers.io) {
        apps.forEach { app ->
            dao.upsert(
                LockedAppEntity(
                    packageName = app.packageName,
                    appName = app.label,
                    isLocked = true,
                    category = app.category,
                )
            )
        }
        if (apps.isNotEmpty()) {
            events.log(
                SecurityEventType.APP_LOCKED,
                SecuritySeverity.INFO,
                "Protection enabled for ${apps.size} app(s)",
            )
        }
    }

    suspend fun unlockAll() = withContext(dispatchers.io) {
        dao.unlockAll()
        events.log(
            SecurityEventType.APP_UNLOCKED,
            SecuritySeverity.WARNING,
            "All apps were unprotected",
        )
    }

    suspend fun lockAll() = withContext(dispatchers.io) {
        dao.lockAll()
        events.log(
            SecurityEventType.APP_LOCKED,
            SecuritySeverity.INFO,
            "Protection re-enabled for all configured apps",
        )
    }

    suspend fun isLocked(packageName: String): Boolean = withContext(dispatchers.io) {
        dao.find(packageName)?.isLocked == true
    }

    suspend fun appName(packageName: String): String? = withContext(dispatchers.io) {
        dao.find(packageName)?.appName ?: installedAppsProvider.labelFor(packageName)
    }

    suspend fun lockedList(): List<LockedApp> = withContext(dispatchers.io) {
        dao.lockedEntities().map { it.toDomain() }
    }

    suspend fun restore(apps: List<LockedApp>) = withContext(dispatchers.io) {
        apps.forEach { app ->
            dao.upsert(
                LockedAppEntity(
                    packageName = app.packageName,
                    appName = app.appName,
                    isLocked = true,
                    category = app.category,
                    lockedAt = app.lockedAt,
                )
            )
        }
    }

    suspend fun clear() = withContext(dispatchers.io) { dao.clear() }

    private fun LockedAppEntity.toDomain() = LockedApp(
        packageName = packageName,
        appName = appName,
        isLocked = isLocked,
        category = category,
        lockedAt = lockedAt,
    )
}
