package com.karan.anuj.core.data.di

// import android.content.Context  // used only by provideDatabase, now in DatabaseModule
// import androidx.room.Room  // used only by provideDatabase, now in DatabaseModule
import com.karan.anuj.core.data.db.AnujDatabase
import com.karan.anuj.core.data.db.AttachmentDao
import com.karan.anuj.core.data.db.ChangeHistoryDao
import com.karan.anuj.core.data.db.ChecklistDao
// import com.karan.anuj.core.data.db.Migrations  // used only by provideDatabase, now in DatabaseModule
import com.karan.anuj.core.data.db.NoteDao
import com.karan.anuj.core.data.db.SnapshotDao
import com.karan.anuj.core.data.db.TagDao
import com.karan.anuj.core.data.db.TaskDao
import com.karan.anuj.core.data.history.RoomChangeHistoryRepository
import com.karan.anuj.core.data.settings.DataStoreSettingsRepository
import com.karan.anuj.core.data.task.DataStoreTaskPreferencesRepository
import com.karan.anuj.core.data.task.FileAttachmentStore
import com.karan.anuj.core.data.task.RoomAttachmentRepository
import com.karan.anuj.core.data.task.RoomChecklistRepository
import com.karan.anuj.core.data.task.RoomNoteRepository
import com.karan.anuj.core.data.task.RoomTagRepository
import com.karan.anuj.core.data.task.RoomTaskRepository
import com.karan.anuj.core.domain.history.ChangeHistoryRepository
import com.karan.anuj.core.domain.settings.SettingsRepository
import com.karan.anuj.core.domain.task.AttachmentFileStore
import com.karan.anuj.core.domain.task.AttachmentRepository
import com.karan.anuj.core.domain.task.ChecklistRepository
import com.karan.anuj.core.domain.task.IdGenerator
import com.karan.anuj.core.domain.task.NoteRepository
import com.karan.anuj.core.domain.task.TagRepository
import com.karan.anuj.core.domain.task.TaskPreferencesRepository
import com.karan.anuj.core.domain.task.TaskRepository
import com.karan.anuj.core.domain.time.TimeSource
// import com.karan.anuj.core.security.DatabasePassphraseProvider  // used only by provideDatabase, now in DatabaseModule
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
// import dagger.hilt.android.qualifiers.ApplicationContext  // used only by provideDatabase, now in DatabaseModule
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
// import net.zetetic.database.sqlcipher.SupportOpenHelperFactory  // used only by provideDatabase, now in DatabaseModule

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

    @Binds
    abstract fun bindTaskRepository(impl: RoomTaskRepository): TaskRepository

    @Binds
    abstract fun bindChecklistRepository(impl: RoomChecklistRepository): ChecklistRepository

    @Binds
    abstract fun bindNoteRepository(impl: RoomNoteRepository): NoteRepository

    @Binds
    abstract fun bindTagRepository(impl: RoomTagRepository): TagRepository

    @Binds
    abstract fun bindAttachmentRepository(impl: RoomAttachmentRepository): AttachmentRepository

    @Binds
    abstract fun bindAttachmentFileStore(impl: FileAttachmentStore): AttachmentFileStore

    @Binds
    abstract fun bindTaskPreferencesRepository(impl: DataStoreTaskPreferencesRepository): TaskPreferencesRepository

    companion object {

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideTimeSource(): TimeSource = TimeSource { System.currentTimeMillis() }

        /** Random ids, so rows made on different phones or restored from a backup can never collide. */
        @Provides
        @Singleton
        fun provideIdGenerator(): IdGenerator = IdGenerator { UUID.randomUUID().toString() }

        /**
         * The database itself is now provided by [DatabaseModule], so tests
         * can replace only that. It used to be provided here:
         *
         *     @Provides
         *     @Singleton
         *     fun provideDatabase(
         *         @ApplicationContext context: Context,
         *         passphrase: DatabasePassphraseProvider,
         *     ): AnujDatabase {
         *         System.loadLibrary("sqlcipher")
         *         return Room.databaseBuilder(context, AnujDatabase::class.java, AnujDatabase.FILE_NAME)
         *             .openHelperFactory(SupportOpenHelperFactory(passphrase.passphrase()))
         *             .addMigrations(*Migrations.ALL)
         *             .build()
         *     }
         */
        @Provides
        fun provideChangeHistoryDao(database: AnujDatabase): ChangeHistoryDao = database.changeHistoryDao()

        @Provides
        fun provideTaskDao(database: AnujDatabase): TaskDao = database.taskDao()

        @Provides
        fun provideChecklistDao(database: AnujDatabase): ChecklistDao = database.checklistDao()

        @Provides
        fun provideNoteDao(database: AnujDatabase): NoteDao = database.noteDao()

        @Provides
        fun provideTagDao(database: AnujDatabase): TagDao = database.tagDao()

        @Provides
        fun provideAttachmentDao(database: AnujDatabase): AttachmentDao = database.attachmentDao()

        @Provides
        fun provideSnapshotDao(database: AnujDatabase): SnapshotDao = database.snapshotDao()
    }
}
