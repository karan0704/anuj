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

    /** Version 3 adds the reminder table and the reminder log. Nothing existing is altered. */
    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            VERSION_3_STATEMENTS.forEach(db::execSQL)
        }
    }

    /**
     * Version 4 has one kind of step. A checklist line was a second, lighter
     * kind kept in its own table; each line becomes a sub-task of the task
     * it was on (ticked lines arrive done, removed lines arrive in the
     * trash), and the two checklist tables are dropped.
     *
     * Room removes the search tables' sync triggers before a migration runs,
     * so the rows added here are not indexed as they go in; the task index is
     * rebuilt at the end to make the new steps searchable.
     */
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            VERSION_4_STATEMENTS.forEach(db::execSQL)
        }
    }

    /** Version 5 adds an index on the time of a change, for the History screen and for clearing old history. */
    val FROM_4_TO_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_change_history_changedAt` ON `change_history` (`changedAt`)")
        }
    }

    /** Version 6 adds places, the stays at them, and the tie between a task and a place. Nothing existing is altered. */
    val FROM_5_TO_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            VERSION_6_STATEMENTS.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(FROM_1_TO_2, FROM_2_TO_3, FROM_3_TO_4, FROM_4_TO_5, FROM_5_TO_6)

    /** Copied from the exported schema `6.json`. */
    private val VERSION_6_STATEMENTS = listOf(
        "CREATE TABLE IF NOT EXISTS `place` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `latitude` REAL NOT NULL, " +
            "`longitude` REAL NOT NULL, `radiusMeters` INTEGER NOT NULL, `kind` TEXT NOT NULL, `isHome` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `place_visit` (`id` TEXT NOT NULL, `placeId` TEXT NOT NULL, `arrivedAt` INTEGER NOT NULL, " +
            "`leftAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`placeId`) REFERENCES `place`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX IF NOT EXISTS `index_place_visit_placeId` ON `place_visit` (`placeId`)",
        "CREATE INDEX IF NOT EXISTS `index_place_visit_arrivedAt` ON `place_visit` (`arrivedAt`)",
        "CREATE TABLE IF NOT EXISTS `task_place` (`taskId` TEXT NOT NULL, `placeId` TEXT NOT NULL, `moment` TEXT NOT NULL, " +
            "PRIMARY KEY(`taskId`), " +
            "FOREIGN KEY(`taskId`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, " +
            "FOREIGN KEY(`placeId`) REFERENCES `place`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX IF NOT EXISTS `index_task_place_placeId` ON `task_place` (`placeId`)",
    )

    /** A line keeps its place among its neighbours by being created that many milliseconds after them. */
    private val VERSION_4_STATEMENTS = listOf(
        "INSERT INTO `task` (`id`, `parentId`, `name`, `description`, `daysOff`, `priority`, `carryCount`, " +
            "`completedAt`, `createdAt`, `updatedAt`, `deletedAt`) " +
            "SELECT `id`, `taskId`, `text`, '', 0, 'NONE', 0, " +
            "CASE WHEN `checked` THEN `updatedAt` END, `createdAt` + `position`, `updatedAt`, `deletedAt` " +
            "FROM `checklist_item`",
        "DROP TABLE IF EXISTS `checklist_item_fts`",
        "DROP TABLE IF EXISTS `checklist_item`",
        "INSERT INTO `task_fts`(`task_fts`) VALUES('rebuild')",
    )

    /** Copied from the exported schema `3.json`. */
    private val VERSION_3_STATEMENTS = listOf(
        "CREATE TABLE IF NOT EXISTS `reminder` (`id` TEXT NOT NULL, `taskId` TEXT, `title` TEXT NOT NULL, " +
            "`schedule` TEXT NOT NULL, `category` TEXT NOT NULL, `style` TEXT NOT NULL, `nagEveryMinutes` INTEGER, " +
            "`nagTimes` INTEGER NOT NULL, `toneUri` TEXT, `enabled` INTEGER NOT NULL, `lastOccurrenceAt` INTEGER, " +
            "`lastFiredAt` INTEGER, `nagsSent` INTEGER NOT NULL, `snoozedUntil` INTEGER, `answeredAt` INTEGER, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`taskId`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX IF NOT EXISTS `index_reminder_taskId` ON `reminder` (`taskId`)",
        "CREATE INDEX IF NOT EXISTS `index_reminder_deletedAt` ON `reminder` (`deletedAt`)",
        "CREATE TABLE IF NOT EXISTS `reminder_event` (`id` TEXT NOT NULL, `reminderId` TEXT, `taskId` TEXT, " +
            "`title` TEXT NOT NULL, `kind` TEXT NOT NULL, `at` INTEGER NOT NULL, `minutes` INTEGER, `reason` TEXT, " +
            "PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_reminder_event_at` ON `reminder_event` (`at`)",
        "CREATE INDEX IF NOT EXISTS `index_reminder_event_reminderId` ON `reminder_event` (`reminderId`)",
    )

    /**
     * Copied from the exported schema `2.json`. The search tables' sync triggers are
     * not listed: Room creates those itself after every migration. The checklist
     * tables made here are removed again by version 4.
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
