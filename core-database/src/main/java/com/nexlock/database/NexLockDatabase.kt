package com.nexlock.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nexlock.database.converter.Converters
import com.nexlock.database.dao.AutomationDao
import com.nexlock.database.dao.IntruderDao
import com.nexlock.database.dao.LockProfileDao
import com.nexlock.database.dao.LockedAppDao
import com.nexlock.database.dao.SecureNoteDao
import com.nexlock.database.dao.SecurityEventDao
import com.nexlock.database.dao.VaultDao
import com.nexlock.database.entity.IntruderIncidentEntity
import com.nexlock.database.entity.LockProfileEntity
import com.nexlock.database.entity.LockScheduleEntity
import com.nexlock.database.entity.LockedAppEntity
import com.nexlock.database.entity.LocationRuleEntity
import com.nexlock.database.entity.SecureNoteEntity
import com.nexlock.database.entity.SecurityEventEntity
import com.nexlock.database.entity.TrustedWifiEntity
import com.nexlock.database.entity.VaultItemEntity

/**
 * Local-only database. There is no remote sync and no INTERNET permission in the manifest.
 *
 * Sensitive note content and vault media never appear here in plaintext - see
 * [com.nexlock.database.entity.SecureNoteEntity] and the encrypted vault file store.
 */
@Database(
    entities = [
        LockedAppEntity::class,
        SecurityEventEntity::class,
        VaultItemEntity::class,
        SecureNoteEntity::class,
        IntruderIncidentEntity::class,
        LockScheduleEntity::class,
        TrustedWifiEntity::class,
        LocationRuleEntity::class,
        LockProfileEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NexLockDatabase : RoomDatabase() {
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun vaultDao(): VaultDao
    abstract fun secureNoteDao(): SecureNoteDao
    abstract fun intruderDao(): IntruderDao
    abstract fun automationDao(): AutomationDao
    abstract fun lockProfileDao(): LockProfileDao

    companion object {
        const val NAME = "nexlock.db"
    }
}
