package com.karan.anuj.core.security.di

import com.karan.anuj.core.domain.lock.AppLockPolicy
import com.karan.anuj.core.security.DatabasePassphraseProvider
import com.karan.anuj.core.security.KeystoreDatabasePassphraseProvider
import com.karan.anuj.core.security.KeystoreSecretStore
import com.karan.anuj.core.security.SecretStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    abstract fun bindPassphraseProvider(impl: KeystoreDatabasePassphraseProvider): DatabasePassphraseProvider

    @Binds
    abstract fun bindSecretStore(impl: KeystoreSecretStore): SecretStore

    companion object {
        @Provides
        fun provideAppLockPolicy(): AppLockPolicy = AppLockPolicy()
    }
}
