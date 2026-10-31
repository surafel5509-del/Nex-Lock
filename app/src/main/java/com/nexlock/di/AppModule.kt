package com.nexlock.di

import com.nexlock.BuildConfig
import com.nexlock.security.risk.SignatureExpectation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the expected signing-certificate digest to the risk assessor.
 * The value comes from the `nexlock.certSha256` Gradle property (see README); when it is empty the
 * integrity check reports "not configured" instead of failing closed, which keeps debug builds usable.
 */
@Singleton
class BuildSignatureExpectation @Inject constructor() : SignatureExpectation {
    override val expectedSha256: String = BuildConfig.EXPECTED_CERT_SHA256
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSignatureExpectation(impl: BuildSignatureExpectation): SignatureExpectation
}
