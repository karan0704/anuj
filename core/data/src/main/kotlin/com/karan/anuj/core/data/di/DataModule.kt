package com.karan.anuj.core.data.di

import android.content.Context
import androidx.room.Room
import com.karan.anuj.core.data.db.AnujDatabase
import com.karan.anuj.core.data.db.ChangeHistoryDao
import com.karan.anuj.core.data.history.RoomChangeHistoryRepository
import com.karan.anuj.core.data.settings.DataStoreSettingsRepository
import com.karan.anuj.core.domain.history.ChangeHistoryRepository
import com.karan.anuj.core.domain.settings.SettingsRepository
import com.karan.anuj.core.domain.time.TimeSource
import com.karan.anuj.core.security.DatabasePassphraseProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** The dispatcher for database and file work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindChangeHistoryRepository(impl: RoomChangeHistoryRepository): ChangeHistoryRepository

    companion object {

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideTimeSource(): TimeSource = TimeSource { System.currentTimeMillis() }

        /**
         * The database file is encrypted with SQLCipher. Building it reads the
         * passphrase from the keystore, so this provider is only ever reached
         * from the IO dispatcher (see the repositories, which take their DAO
         * lazily).
         */
        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context,
            passphrase: DatabasePassphraseProvider,
        ): AnujDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AnujDatabase::class.java, AnujDatabase.FILE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase.passphrase()))
                .build()
        }

        @Provides
        fun provideChangeHistoryDao(database: AnujDatabase): ChangeHistoryDao = database.changeHistoryDao()
    }
}
