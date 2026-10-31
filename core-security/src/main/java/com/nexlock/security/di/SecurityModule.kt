package com.nexlock.security.di

import com.nexlock.security.crypto.CryptoManager
import com.nexlock.security.crypto.KeystoreCryptoManager
import com.nexlock.security.crypto.KeystorePepperProvider
import com.nexlock.security.crypto.Pbkdf2SecretHasher
import com.nexlock.security.crypto.PepperProvider
import com.nexlock.security.crypto.SecretHasher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindCryptoManager(impl: KeystoreCryptoManager): CryptoManager

    @Binds
    @Singleton
    abstract fun bindSecretHasher(impl: Pbkdf2SecretHasher): SecretHasher

    @Binds
    @Singleton
    abstract fun bindPepperProvider(impl: KeystorePepperProvider): PepperProvider
}
