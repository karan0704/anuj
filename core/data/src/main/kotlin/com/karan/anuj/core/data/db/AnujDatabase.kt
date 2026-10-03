package com.karan.anuj.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's single database. Any change to an entity here needs a new version
 * number and a migration; the exported schema under `core/data/schemas` shows
 * what each version looked like.
 */
@Database(
    entities = [ChangeHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AnujDatabase : RoomDatabase() {
    abstract fun changeHistoryDao(): ChangeHistoryDao

    companion object {
        const val FILE_NAME = "anuj.db"
    }
}
