package com.karan.anuj.core.data.di

import android.content.Context
import androidx.room.Room
import com.karan.anuj.core.data.db.AnujDatabase
import com.karan.anuj.core.data.db.Migrations
import com.karan.anuj.core.security.DatabasePassphraseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * How the database itself is opened. This is the only piece that needs the
 * phone (the encryption library and the keystore), so it sits in a module of
 * its own: tests swap just this one for an in-memory database and keep
 * every other binding exactly as the app has it.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * The database file is encrypted with SQLCipher. Building it reads the
     * passphrase from the keystore, so this provider is only ever reached
     * from the IO dispatcher (see the repositories, which take their DAO
     * lazily).
     *
     * Every migration is registered, so an older installed database is
     * upgraded in place.
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
            .addMigrations(*Migrations.ALL)
            .build()
    }
}
