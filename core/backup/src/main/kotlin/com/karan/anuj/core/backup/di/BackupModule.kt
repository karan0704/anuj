package com.karan.anuj.core.backup.di

import com.karan.anuj.core.backup.DefaultBackupRepository
import com.karan.anuj.core.domain.backup.BackupRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    abstract fun bindBackupRepository(impl: DefaultBackupRepository): BackupRepository
}
