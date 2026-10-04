package com.karan.anuj.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * How an installed copy of the app moves from one database version to the
 * next without losing data. Every statement here matches the exported schema
 * of the target version in `core/data/schemas`; `MigrationTest` opens an old
 * database through these and lets Room compare the result against what the
 * current code expects.
 */
object Migrations {

    /** Version 2 adds the task tables. Nothing existing is altered, so no data is touched. */
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            VERSION_2_STATEMENTS.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(FROM_1_TO_2)

    /**
     * Copied from the exported schema `2.json`. The search tables' sync triggers are
     * not listed: Room creates those itself after every migration.
     */
    private val VERSION_2_STATEMENTS = listOf(
        "CREATE TABLE IF NOT EXISTS `task` (`id` TEXT NOT NULL, `parentId` TEXT, " +
            "`name` TEXT NOT NULL, `description` TEXT NOT NULL, `dueDay` INTEGER, `dueMinute` INTEGER, " +
            "`repetition` TEXT, `daysOff` INTEGER NOT NULL, `priority` TEXT NOT NULL, `energy` TEXT, " +
            "`estimatedMinutes` INTEGER, `carryOver` TEXT, `carryCount` INTEGER NOT NULL, " +
            "`completedAt` INTEGER, `missedAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`parentId`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX IF NOT EXISTS `index_task_parentId` ON `task` (`parentId`)",
        "CREATE INDEX IF NOT EXISTS `index_task_dueDay` ON `task` (`dueDay`)",
        "CREATE INDEX IF NOT EXISTS `index_task_completedAt` ON `task` (`completedAt`)",
        "CREATE INDEX IF NOT EXISTS `index_task_deletedAt` ON `task` (`deletedAt`)",
        "CREATE TABLE IF NOT EXISTS `checklist_item` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, " +
            "`text` TEXT NOT NULL, `checked` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
            "PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`taskId`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX IF NOT EXISTS `index_checklist_item_taskId` ON `checklist_item` (`taskId`)",
        "CREATE TABLE IF NOT EXISTS `note` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, " +
            "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "`deletedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`taskId`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX IF NOT EXISTS `index_note_taskId` ON `note` (`taskId`)",
        "CREATE TABLE IF NOT EXISTS `tag` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`colorIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `task_tag` (`taskId` TEXT NOT NULL, `tagId` TEXT NOT NULL, " +
            "PRIMARY KEY(`taskId`, `tagId`), " +
            "FOREIGN KEY(`taskId`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, " +
            "FOREIGN KEY(`tagId`) REFERENCES `tag`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX IF NOT EXISTS `index_task_tag_tagId` ON `task_tag` (`tagId`)",
        "CREATE TABLE IF NOT EXISTS `attachment` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, " +
            "`fileName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "`deletedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`taskId`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX IF NOT EXISTS `index_attachment_taskId` ON `attachment` (`taskId`)",
        "CREATE TABLE IF NOT EXISTS `task_occurrence` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, " +
            "`day` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `at` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`taskId`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX IF NOT EXISTS `index_task_occurrence_taskId` ON `task_occurrence` (`taskId`)",
        "CREATE INDEX IF NOT EXISTS `index_task_occurrence_at` ON `task_occurrence` (`at`)",
        "CREATE VIRTUAL TABLE IF NOT EXISTS `task_fts` USING FTS4(`name` TEXT NOT NULL, " +
            "`description` TEXT NOT NULL, content=`task`)",
        "CREATE VIRTUAL TABLE IF NOT EXISTS `note_fts` USING FTS4(`text` TEXT NOT NULL, " +
            "content=`note`)",
        "CREATE VIRTUAL TABLE IF NOT EXISTS `checklist_item_fts` USING FTS4(`text` TEXT NOT NULL, " +
            "content=`checklist_item`)",
    )
}
