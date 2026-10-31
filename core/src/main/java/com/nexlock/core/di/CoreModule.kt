package com.nexlock.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nexlock.common.util.DefaultDispatcherProvider
import com.nexlock.common.util.DispatcherProvider
import com.nexlock.common.util.NexConstants
import com.nexlock.common.util.SystemTimeProvider
import com.nexlock.common.util.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(NexConstants.SETTINGS_DATASTORE) },
        )

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()
}
