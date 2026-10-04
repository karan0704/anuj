package com.karan.anuj.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's single database. Any change to an entity here needs a new version
 * number and a migration; the exported schema under `core/data/schemas` shows
 * what each version looked like.
 */
@Database(
    entities = [
        ChangeHistoryEntity::class,
        TaskEntity::class,
        ChecklistItemEntity::class,
        NoteEntity::class,
        TagEntity::class,
        TaskTagEntity::class,
        AttachmentEntity::class,
        TaskOccurrenceEntity::class,
        TaskFtsEntity::class,
        NoteFtsEntity::class,
        ChecklistItemFtsEntity::class,
        ReminderEntity::class,
        ReminderEventEntity::class,
    ],
    version = AnujDatabase.VERSION,
    exportSchema = true,
)
abstract class AnujDatabase : RoomDatabase() {
    abstract fun changeHistoryDao(): ChangeHistoryDao
    abstract fun taskDao(): TaskDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val FILE_NAME = "anuj.db"
        const val VERSION = 3
    }
}
