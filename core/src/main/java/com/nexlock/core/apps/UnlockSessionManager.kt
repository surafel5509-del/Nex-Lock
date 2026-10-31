package com.nexlock.core.apps

import com.nexlock.common.util.TimeProvider
import com.nexlock.core.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory unlock sessions.
 *
 * * A timeout of 0 means "unlock until the user leaves this app" (immediate re-lock).
 * * A positive timeout keeps the app unlocked for N seconds after each successful unlock.
 * * Temporary unlocks (user initiated, minutes) are persisted separately in settings so they
 *   survive a process restart for the requested duration.
 * * The map is intentionally not persisted: after process death everything re-locks.
 */
@Singleton
class UnlockSessionManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) {

    private val mutex = Mutex()
    private val unlockedUntil = mutableMapOf<String, Long>()
    private var foregroundPackage: String? = null

    suspend fun isUnlocked(packageName: String): Boolean = mutex.withLock {
        val now = timeProvider.nowMillis()
        val settings = settingsRepository.current()
        if (settings.temporaryUnlockUntilMillis > now) return@withLock true

        val until = unlockedUntil[packageName] ?: return@withLock false
        if (until == UNTIL_LEAVE) return@withLock true
        if (until > now) return@withLock true
        unlockedUntil.remove(packageName)
        false
    }

    /** Called after a successful authentication for [packageName]. */
    suspend fun grantUnlock(packageName: String) = mutex.withLock {
        val settings = settingsRepository.current()
        val now = timeProvider.nowMillis()
        unlockedUntil[packageName] = if (settings.lockTimeoutSeconds <= 0) {
            UNTIL_LEAVE
        } else {
            now + settings.lockTimeoutSeconds * 1000L
        }
    }

    /**
     * Tracks foreground transitions and implements "re-lock immediately when leaving".
     * Returns true when a previously unlocked app was re-locked by this transition.
     */
    suspend fun onForegroundChanged(packageName: String): Boolean = mutex.withLock {
        val previous = foregroundPackage
        foregroundPackage = packageName
        if (previous == null || previous == packageName) return@withLock false

        val settings = settingsRepository.current()
        if (!settings.relockOnScreenOff && settings.lockTimeoutSeconds > 0) return@withLock false

        val previousUntil = unlockedUntil[previous] ?: return@withLock false
        if (previousUntil == UNTIL_LEAVE || settings.lockTimeoutSeconds == 0) {
            unlockedUntil.remove(previous)
            true
        } else {
            false
        }
    }

    /** Screen-off / user-initiated re-lock. */
    suspend fun relockAll() = mutex.withLock {
        unlockedUntil.clear()
    }

    suspend fun relock(packageName: String) = mutex.withLock {
        unlockedUntil.remove(packageName)
        Unit
    }

    suspend fun startTemporaryUnlock(minutes: Int) {
        val until = timeProvider.nowMillis() + minutes.coerceIn(1, 120) * 60_000L
        settingsRepository.setTemporaryUnlockUntil(until)
        mutex.withLock { unlockedUntil.clear() }
    }

    suspend fun endTemporaryUnlock() {
        settingsRepository.setTemporaryUnlockUntil(0L)
        mutex.withLock { unlockedUntil.clear() }
    }

    suspend fun temporaryUnlockRemainingMillis(): Long {
        val until = settingsRepository.current().temporaryUnlockUntilMillis
        return (until - timeProvider.nowMillis()).coerceAtLeast(0L)
    }

    companion object {
        private const val UNTIL_LEAVE = Long.MAX_VALUE
    }
}
