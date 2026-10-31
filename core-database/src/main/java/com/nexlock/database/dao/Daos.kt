package com.nexlock.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.nexlock.database.entity.IntruderIncidentEntity
import com.nexlock.database.entity.LockProfileEntity
import com.nexlock.database.entity.LockScheduleEntity
import com.nexlock.database.entity.LockedAppEntity
import com.nexlock.database.entity.LocationRuleEntity
import com.nexlock.database.entity.SecureNoteEntity
import com.nexlock.database.entity.SecurityEventEntity
import com.nexlock.database.entity.TrustedWifiEntity
import com.nexlock.database.entity.VaultItemEntity
import com.nexlock.common.model.VaultKind
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {

    @Query("SELECT * FROM locked_apps ORDER BY appName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps WHERE isLocked = 1 ORDER BY appName COLLATE NOCASE ASC")
    fun observeLocked(): Flow<List<LockedAppEntity>>

    @Query("SELECT packageName FROM locked_apps WHERE isLocked = 1")
    fun observeLockedPackages(): Flow<List<String>>

    @Query("SELECT packageName FROM locked_apps WHERE isLocked = 1")
    suspend fun lockedPackages(): List<String>

    @Query("SELECT COUNT(*) FROM locked_apps WHERE isLocked = 1")
    fun observeLockedCount(): Flow<Int>

    @Query("SELECT * FROM locked_apps WHERE isLocked = 1 ORDER BY appName COLLATE NOCASE ASC")
    suspend fun lockedEntities(): List<LockedAppEntity>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun find(packageName: String): LockedAppEntity?

    @Upsert
    suspend fun upsert(entity: LockedAppEntity)

    @Query("UPDATE locked_apps SET isLocked = :locked WHERE packageName = :packageName")
    suspend fun setLocked(packageName: String, locked: Boolean)

    @Query("UPDATE locked_apps SET isLocked = 1")
    suspend fun lockAll()

    @Query("UPDATE locked_apps SET isLocked = 0")
    suspend fun unlockAll()

    @Query("DELETE FROM locked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM locked_apps")
    suspend fun clear()
}

@Dao
interface SecurityEventDao {

    @Insert
    suspend fun insert(event: SecurityEventEntity): Long

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SecurityEventEntity>>

    @Query("SELECT COUNT(*) FROM security_events")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM security_events WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM security_events")
    suspend fun clear()
}

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE kind = :kind ORDER BY createdAt DESC")
    fun observeByKind(kind: VaultKind): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE kind = :kind ORDER BY createdAt DESC")
    suspend fun byKind(kind: VaultKind): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    suspend fun all(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): VaultItemEntity?

    @Query("SELECT COUNT(*) FROM vault_items WHERE kind = :kind")
    fun observeCount(kind: VaultKind): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM vault_items")
    fun observeTotalBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: VaultItemEntity): Long

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM vault_items")
    suspend fun clear()
}

@Dao
interface SecureNoteDao {

    @Query("SELECT * FROM secure_notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SecureNoteEntity>>

    @Query("SELECT * FROM secure_notes ORDER BY updatedAt DESC")
    suspend fun all(): List<SecureNoteEntity>

    @Query("SELECT * FROM secure_notes WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): SecureNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: SecureNoteEntity): Long

    @Query("UPDATE secure_notes SET titleCipher = :titleCipher, contentCipher = :contentCipher, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: Long, titleCipher: String, contentCipher: String, updatedAt: Long)

    @Query("DELETE FROM secure_notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM secure_notes")
    suspend fun clear()
}

@Dao
interface IntruderDao {

    @Query("SELECT * FROM intruder_incidents ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<IntruderIncidentEntity>>

    @Query("SELECT COUNT(*) FROM intruder_incidents")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM intruder_incidents ORDER BY timestamp DESC")
    suspend fun all(): List<IntruderIncidentEntity>

    @Insert
    suspend fun insert(incident: IntruderIncidentEntity): Long

    @Query("DELETE FROM intruder_incidents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM intruder_incidents")
    suspend fun clear()
}

@Dao
interface AutomationDao {

    @Query("SELECT * FROM lock_schedules ORDER BY startMinute ASC")
    fun observeSchedules(): Flow<List<LockScheduleEntity>>

    @Query("SELECT * FROM lock_schedules")
    suspend fun schedules(): List<LockScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: LockScheduleEntity): Long

    @Query("UPDATE lock_schedules SET enabled = :enabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM lock_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    @Query("SELECT * FROM trusted_wifi ORDER BY addedAt DESC")
    fun observeTrustedWifi(): Flow<List<TrustedWifiEntity>>

    @Query("SELECT * FROM trusted_wifi WHERE enabled = 1")
    suspend fun trustedWifi(): List<TrustedWifiEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrustedWifi(wifi: TrustedWifiEntity)

    @Query("UPDATE trusted_wifi SET enabled = :enabled WHERE ssid = :ssid")
    suspend fun setTrustedWifiEnabled(ssid: String, enabled: Boolean)

    @Query("DELETE FROM trusted_wifi WHERE ssid = :ssid")
    suspend fun deleteTrustedWifi(ssid: String)

    @Query("SELECT * FROM location_rules ORDER BY label ASC")
    fun observeLocations(): Flow<List<LocationRuleEntity>>

    @Query("SELECT * FROM location_rules WHERE enabled = 1")
    suspend fun enabledLocations(): List<LocationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(rule: LocationRuleEntity): Long

    @Query("UPDATE location_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setLocationEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM location_rules WHERE id = :id")
    suspend fun deleteLocation(id: Long)
}

@Dao
interface LockProfileDao {

    @Query("SELECT * FROM lock_profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<LockProfileEntity>>

    @Query("SELECT * FROM lock_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<LockProfileEntity?>

    @Query("SELECT * FROM lock_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun active(): LockProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: LockProfileEntity): Long

    @Query("UPDATE lock_profiles SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE lock_profiles SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("DELETE FROM lock_profiles WHERE id = :id")
    suspend fun delete(id: Long)
}
