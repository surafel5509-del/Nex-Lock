package com.nexlock.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nexlock.database.NexLockDatabase
import com.nexlock.database.dao.AutomationDao
import com.nexlock.database.dao.IntruderDao
import com.nexlock.database.dao.LockProfileDao
import com.nexlock.database.dao.LockedAppDao
import com.nexlock.database.dao.SecureNoteDao
import com.nexlock.database.dao.SecurityEventDao
import com.nexlock.database.dao.VaultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexLockDatabase =
        Room.databaseBuilder(context, NexLockDatabase::class.java, NexLockDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun provideLockedAppDao(db: NexLockDatabase): LockedAppDao = db.lockedAppDao()

    @Provides
    fun provideSecurityEventDao(db: NexLockDatabase): SecurityEventDao = db.securityEventDao()

    @Provides
    fun provideVaultDao(db: NexLockDatabase): VaultDao = db.vaultDao()

    @Provides
    fun provideSecureNoteDao(db: NexLockDatabase): SecureNoteDao = db.secureNoteDao()

    @Provides
    fun provideIntruderDao(db: NexLockDatabase): IntruderDao = db.intruderDao()

    @Provides
    fun provideAutomationDao(db: NexLockDatabase): AutomationDao = db.automationDao()

    @Provides
    fun provideLockProfileDao(db: NexLockDatabase): LockProfileDao = db.lockProfileDao()
}
