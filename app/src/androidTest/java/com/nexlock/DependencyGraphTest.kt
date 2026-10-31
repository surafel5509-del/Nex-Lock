package com.nexlock

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexlock.core.apps.LockEngine
import com.nexlock.core.apps.LockedAppsRepository
import com.nexlock.core.auth.AuthRepository
import com.nexlock.core.automation.AutomationRepository
import com.nexlock.core.backup.BackupRepository
import com.nexlock.core.intruder.IntruderRepository
import com.nexlock.core.notifications.NexNotifier
import com.nexlock.core.profiles.ProfileRepository
import com.nexlock.core.security.SecurityPostureRepository
import com.nexlock.core.settings.SettingsRepository
import com.nexlock.core.vault.VaultRepository
import com.nexlock.monitor.MonitorCoordinator
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the whole dependency graph. A missing Hilt binding or an unsatisfied constructor
 * fails here instead of at runtime on a user's device.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DependencyGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var lockedAppsRepository: LockedAppsRepository
    @Inject lateinit var lockEngine: LockEngine
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var vaultRepository: VaultRepository
    @Inject lateinit var intruderRepository: IntruderRepository
    @Inject lateinit var automationRepository: AutomationRepository
    @Inject lateinit var backupRepository: BackupRepository
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var postureRepository: SecurityPostureRepository
    @Inject lateinit var notifier: NexNotifier
    @Inject lateinit var monitorCoordinator: MonitorCoordinator

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun graphIsComplete() {
        assertNotNull(settingsRepository)
        assertNotNull(lockedAppsRepository)
        assertNotNull(lockEngine)
        assertNotNull(authRepository)
        assertNotNull(vaultRepository)
        assertNotNull(intruderRepository)
        assertNotNull(automationRepository)
        assertNotNull(backupRepository)
        assertNotNull(profileRepository)
        assertNotNull(postureRepository)
        assertNotNull(notifier)
        assertNotNull(monitorCoordinator)
    }
}
