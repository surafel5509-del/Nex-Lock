package com.nexlock.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexlock.common.model.AppCategory
import com.nexlock.common.model.AuthMode
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.model.VaultKind
import com.nexlock.database.entity.IntruderIncidentEntity
import com.nexlock.database.entity.LockProfileEntity
import com.nexlock.database.entity.LockedAppEntity
import com.nexlock.database.entity.SecureNoteEntity
import com.nexlock.database.entity.SecurityEventEntity
import com.nexlock.database.entity.VaultItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexLockDatabaseTest {

    private lateinit var database: NexLockDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NexLockDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun lockedAppsFlowReflectsLockState() = runBlocking {
        val dao = database.lockedAppDao()
        dao.upsert(LockedAppEntity("com.example.bank", "Bank", true, AppCategory.FINANCE))
        dao.upsert(LockedAppEntity("com.example.notes", "Notes", false, AppCategory.PRODUCTIVITY))

        assertEquals(1, dao.observeLockedCount().first())
        assertEquals(listOf("com.example.bank"), dao.lockedPackages())

        dao.setLocked("com.example.notes", true)
        assertEquals(2, dao.observeLockedCount().first())

        dao.unlockAll()
        assertEquals(0, dao.observeLockedCount().first())

        dao.lockAll()
        assertEquals(listOf("com.example.bank", "com.example.notes"), dao.lockedPackages().sorted())

        dao.delete("com.example.bank")
        assertNull(dao.find("com.example.bank"))
    }

    @Test
    fun securityEventsAreOrderedAndPruned() = runBlocking {
        val dao = database.securityEventDao()
        dao.insert(
            SecurityEventEntity(
                type = SecurityEventType.APP_LOCKED,
                severity = SecuritySeverity.INFO,
                message = "Lock shown",
                timestamp = 1_000L,
            )
        )
        dao.insert(
            SecurityEventEntity(
                type = SecurityEventType.AUTH_FAILURE,
                severity = SecuritySeverity.WARNING,
                message = "Failed attempt",
                timestamp = 2_000L,
            )
        )

        val events = dao.observeRecent(10).first()
        assertEquals(2, events.size)
        assertEquals("Failed attempt", events.first().message)
        assertEquals(2, dao.observeCount().first())

        dao.deleteOlderThan(1_500L)
        assertEquals(1, dao.observeCount().first())

        dao.clear()
        assertEquals(0, dao.observeCount().first())
    }

    @Test
    fun vaultItemsAggregateBytesPerKind() = runBlocking {
        val dao = database.vaultDao()
        dao.insert(
            VaultItemEntity(
                kind = VaultKind.PHOTO,
                displayName = "a.jpg",
                fileName = "a.enc",
                mimeType = "image/jpeg",
                sizeBytes = 1_024L,
            )
        )
        dao.insert(
            VaultItemEntity(
                kind = VaultKind.DOCUMENT,
                displayName = "b.pdf",
                fileName = "b.enc",
                mimeType = "application/pdf",
                sizeBytes = 2_048L,
            )
        )
        assertEquals(3_072L, dao.observeTotalBytes().first())
        assertEquals(1, dao.byKind(VaultKind.PHOTO).size)
        assertNotNull(dao.find(1L))

        dao.delete(1L)
        assertEquals(1, dao.all().size)
    }

    @Test
    fun secureNotesStoreCiphertextOnly() = runBlocking {
        val dao = database.secureNoteDao()
        val id = dao.insert(
            SecureNoteEntity(titleCipher = "cipher-title", contentCipher = "cipher-body")
        )
        val stored = dao.find(id)
        assertNotNull(stored)
        assertEquals("cipher-title", stored!!.titleCipher)

        dao.update(id, "new-title-cipher", "new-body-cipher", 5_000L)
        val updated = dao.find(id)!!
        assertEquals("new-title-cipher", updated.titleCipher)
        assertEquals(5_000L, updated.updatedAt)

        dao.delete(id)
        assertNull(dao.find(id))
    }

    @Test
    fun intruderIncidentsAreDeletable() = runBlocking {
        val dao = database.intruderDao()
        dao.insert(IntruderIncidentEntity(failedAttempts = 3, packageName = "com.example.bank"))
        dao.insert(IntruderIncidentEntity(failedAttempts = 5, photoFileName = "shot.enc"))

        assertEquals(2, dao.all().size)
        assertEquals(2, dao.observeCount().first())

        dao.delete(dao.all().first().id)
        assertEquals(1, dao.all().size)

        dao.clear()
        assertEquals(0, dao.all().size)
    }

    @Test
    fun enumConvertersRoundTrip() = runBlocking {
        val dao = database.lockProfileDao()
        dao.insert(
            LockProfileEntity(
                name = "Travel",
                authMode = AuthMode.PATTERN,
                timeoutSeconds = 60,
                isActive = true,
            )
        )
        val active = dao.active()
        assertNotNull(active)
        assertEquals(AuthMode.PATTERN, active!!.authMode)

        // Only one profile may be active: activation clears the previous flag first.
        dao.insert(LockProfileEntity(name = "Home", authMode = AuthMode.PIN, timeoutSeconds = 0))
        val home = dao.observeAll().first().first { it.name == "Home" }
        dao.clearActive()
        dao.activate(home.id)
        assertEquals("Home", dao.active()?.name)
        assertFalse(dao.observeAll().first().first { it.name == "Travel" }.isActive)
    }
}
