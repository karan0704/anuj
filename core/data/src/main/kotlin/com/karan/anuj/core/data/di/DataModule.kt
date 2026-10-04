package com.karan.anuj.core.data.di

import com.karan.anuj.core.data.db.AnujDatabase
import com.karan.anuj.core.data.db.AttachmentDao
import com.karan.anuj.core.data.db.ChangeHistoryDao
import com.karan.anuj.core.data.db.NoteDao
import com.karan.anuj.core.data.db.ReminderDao
import com.karan.anuj.core.data.db.SnapshotDao
import com.karan.anuj.core.data.db.TagDao
import com.karan.anuj.core.data.db.TaskDao
import com.karan.anuj.core.data.history.RoomChangeHistoryRepository
import com.karan.anuj.core.data.reminder.DataStoreReminderSettingsRepository
import com.karan.anuj.core.data.reminder.RoomReminderRepository
import com.karan.anuj.core.data.voice.DataStoreVoiceSettingsRepository
import com.karan.anuj.core.data.settings.DataStoreSettingsRepository
import com.karan.anuj.core.data.task.DataStoreTaskPreferencesRepository
import com.karan.anuj.core.data.task.FileAttachmentStore
import com.karan.anuj.core.data.task.RoomAttachmentRepository
import com.karan.anuj.core.data.task.RoomNoteRepository
import com.karan.anuj.core.data.task.RoomTagRepository
import com.karan.anuj.core.data.task.RoomTaskRepository
import com.karan.anuj.core.domain.history.ChangeHistoryRepository
import com.karan.anuj.core.domain.reminder.ReminderRepository
import com.karan.anuj.core.domain.reminder.ReminderSettingsRepository
import com.karan.anuj.core.domain.reminder.ZoneSource
import com.karan.anuj.core.domain.settings.SettingsRepository
import com.karan.anuj.core.domain.task.AttachmentFileStore
import com.karan.anuj.core.domain.task.AttachmentRepository
import com.karan.anuj.core.domain.task.IdGenerator
import com.karan.anuj.core.domain.task.NoteRepository
import com.karan.anuj.core.domain.task.TagRepository
import com.karan.anuj.core.domain.task.TaskPreferencesRepository
import com.karan.anuj.core.domain.task.TaskRepository
import com.karan.anuj.core.domain.time.TimeSource
import com.karan.anuj.core.domain.voice.VoiceSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import java.util.UUID
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

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
    abstract fun bindNoteRepository(impl: RoomNoteRepository): NoteRepository

    @Binds
    abstract fun bindTagRepository(impl: RoomTagRepository): TagRepository

    @Binds
    abstract fun bindAttachmentRepository(impl: RoomAttachmentRepository): AttachmentRepository

    @Binds
    abstract fun bindAttachmentFileStore(impl: FileAttachmentStore): AttachmentFileStore

    @Binds
    abstract fun bindTaskPreferencesRepository(impl: DataStoreTaskPreferencesRepository): TaskPreferencesRepository

    @Binds
    abstract fun bindReminderRepository(impl: RoomReminderRepository): ReminderRepository

    @Binds
    abstract fun bindReminderSettingsRepository(impl: DataStoreReminderSettingsRepository): ReminderSettingsRepository

    @Binds
    abstract fun bindVoiceSettingsRepository(impl: DataStoreVoiceSettingsRepository): VoiceSettingsRepository

    companion object {

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideTimeSource(): TimeSource = TimeSource { System.currentTimeMillis() }

        /** Read each time it is asked for, so a trip to another time zone is picked up without a restart. */
        @Provides
        @Singleton
        fun provideZoneSource(): ZoneSource = ZoneSource { ZoneId.systemDefault() }

        /** Random ids, so rows made on different phones or restored from a backup can never collide. */
        @Provides
        @Singleton
        fun provideIdGenerator(): IdGenerator = IdGenerator { UUID.randomUUID().toString() }

        /** The database itself is provided by [DatabaseModule], so tests can replace only that. */
        @Provides
        fun provideChangeHistoryDao(database: AnujDatabase): ChangeHistoryDao = database.changeHistoryDao()

        @Provides
        fun provideTaskDao(database: AnujDatabase): TaskDao = database.taskDao()

        @Provides
        fun provideNoteDao(database: AnujDatabase): NoteDao = database.noteDao()

        @Provides
        fun provideTagDao(database: AnujDatabase): TagDao = database.tagDao()

        @Provides
        fun provideAttachmentDao(database: AnujDatabase): AttachmentDao = database.attachmentDao()

        @Provides
        fun provideSnapshotDao(database: AnujDatabase): SnapshotDao = database.snapshotDao()

        @Provides
        fun provideReminderDao(database: AnujDatabase): ReminderDao = database.reminderDao()
    }
}
