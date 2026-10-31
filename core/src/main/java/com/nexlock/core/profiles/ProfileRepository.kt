package com.nexlock.core.profiles

import com.nexlock.common.model.AuthMode
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.database.dao.LockProfileDao
import com.nexlock.database.entity.LockProfileEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class LockProfile(
    val id: Long,
    val name: String,
    val authMode: AuthMode,
    val timeoutSeconds: Int,
    val isActive: Boolean,
)

/**
 * Lock profiles bundle "how long an app stays unlocked" with a friendly name.
 * The authentication secret itself is global, so switching profiles never silently changes the
 * credential type the user must remember.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val dao: LockProfileDao,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {

    val profiles: Flow<List<LockProfile>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun ensureDefaultProfile() = withContext(dispatchers.io) {
        if (dao.active() == null) {
            val settings = settingsRepository.current()
            dao.insert(
                LockProfileEntity(
                    name = "Default",
                    authMode = settings.authMode,
                    timeoutSeconds = settings.lockTimeoutSeconds,
                    isActive = true,
                )
            )
        }
    }

    suspend fun addProfile(name: String, timeoutSeconds: Int): Long = withContext(dispatchers.io) {
        val settings = settingsRepository.current()
        dao.insert(
            LockProfileEntity(
                name = name.trim().ifBlank { "Profile" },
                authMode = settings.authMode,
                timeoutSeconds = timeoutSeconds.coerceIn(0, 3600),
            )
        )
    }

    suspend fun activate(profile: LockProfile) = withContext(dispatchers.io) {
        dao.clearActive()
        dao.activate(profile.id)
        settingsRepository.setLockTimeoutSeconds(profile.timeoutSeconds)
    }

    suspend fun delete(profile: LockProfile) = withContext(dispatchers.io) {
        dao.delete(profile.id)
        if (profile.isActive) {
            val settings = settingsRepository.current()
            dao.insert(
                LockProfileEntity(
                    name = "Default",
                    authMode = settings.authMode,
                    timeoutSeconds = settings.lockTimeoutSeconds,
                    isActive = true,
                )
            )
        }
    }

    suspend fun active(): LockProfile? = withContext(dispatchers.io) { dao.active()?.toDomain() }

    private fun LockProfileEntity.toDomain() = LockProfile(
        id = id,
        name = name,
        authMode = authMode,
        timeoutSeconds = timeoutSeconds,
        isActive = isActive,
    )
}
