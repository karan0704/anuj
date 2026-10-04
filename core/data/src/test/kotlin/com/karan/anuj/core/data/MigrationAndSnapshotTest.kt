package com.karan.anuj.core.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.karan.anuj.core.data.db.AnujDatabase
import com.karan.anuj.core.data.db.AttachmentEntity
import com.karan.anuj.core.data.db.ChangeHistoryEntity
import com.karan.anuj.core.data.db.DatabaseSnapshot
import com.karan.anuj.core.data.db.Migrations
import com.karan.anuj.core.data.db.NoteEntity
import com.karan.anuj.core.data.db.PlaceEntity
import com.karan.anuj.core.data.db.PlaceVisitEntity
import com.karan.anuj.core.data.db.ReminderEntity
import com.karan.anuj.core.data.db.ReminderEventEntity
import com.karan.anuj.core.data.db.SnapshotFormat
import com.karan.anuj.core.data.db.StampColumns
import com.karan.anuj.core.data.db.TagEntity
import com.karan.anuj.core.data.db.TaskEntity
import com.karan.anuj.core.data.db.TaskOccurrenceEntity
import com.karan.anuj.core.data.db.TaskPlaceEntity
import com.karan.anuj.core.data.db.TaskTagEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationAndSnapshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: AnujDatabase? = null

    @After
    fun close() {
        db?.close()
    }

    private val stamps = StampColumns(createdAt = 1, updatedAt = 2, deletedAt = null)

    /**
     * Builds a database exactly as an installed older version left it, from
     * the schema file exported when that version was current.
     */
    private fun createDatabaseAtVersion(version: Int, name: String, fill: (SupportSQLiteDatabase) -> Unit) {
        val schema = context.assets
            .open("${AnujDatabase::class.java.name}/$version.json")
            .bufferedReader().use { it.readText() }
            .let { JSONObject(it).getJSONObject("database") }

        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
        fill(helper.writableDatabase)
        helper.close()
    }

    private fun openCurrent(name: String): AnujDatabase =
        Room.databaseBuilder(context, AnujDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    @Test
    fun `a version 1 database upgrades with its history kept and tasks and search working`() = runTest {
        createDatabaseAtVersion(1, "upgrade.db") {
            it.execSQL(
                "INSERT INTO change_history (tableName, rowId, field, oldValue, newValue, changedAt) " +
                    "VALUES ('settings', 'app', 'themeMode', 'SYSTEM', 'DARK', 42)",
            )
        }

        /** Opening runs the migration; Room then compares every table with what the code expects and throws on any difference. */
        val upgraded = openCurrent("upgrade.db")
        upgraded.openHelper.writableDatabase

        val history = upgraded.snapshotDao().changeHistory().single()
        assertEquals("DARK", history.newValue)
        assertEquals(42, history.changedAt)

        upgraded.taskDao().save(listOf(TaskEntity(id = "t", name = "Buy milk", stamps = stamps)), emptyList())
        assertEquals(
            "the search index must be kept in step on a migrated database too",
            listOf("Buy milk"),
            upgraded.taskDao().search("milk*", 10).map { it.name },
        )
        assertEquals(AnujDatabase.VERSION, upgraded.openHelper.readableDatabase.version)
    }

    @Test
    fun `a version 3 database upgrades with its checklist lines turned into steps`() = runTest {
        createDatabaseAtVersion(3, "upgrade3.db") {
            it.execSQL(
                "INSERT INTO task (id, name, description, daysOff, priority, carryCount, createdAt, updatedAt) " +
                    "VALUES ('home', 'Leaving home', '', 0, 'NONE', 0, 1, 1)",
            )
            it.execSQL(
                "INSERT INTO checklist_item (id, taskId, text, checked, position, createdAt, updatedAt, deletedAt) VALUES " +
                    "('keys', 'home', 'Keys in pocket', 1, 0, 10, 20, NULL), " +
                    "('door', 'home', 'Door locked', 0, 1, 10, 20, NULL), " +
                    "('gone', 'home', 'Old line', 0, 2, 10, 20, 30)",
            )
        }

        val upgraded = openCurrent("upgrade3.db")

        val steps = upgraded.snapshotDao().tasks().filter { it.parentId == "home" }.sortedBy { it.stamps.createdAt }
        assertEquals(listOf("Keys in pocket", "Door locked", "Old line"), steps.map { it.name })
        assertEquals("a ticked line arrives done", listOf(20L, null, null), steps.map { it.completedAt })
        assertEquals("a removed line arrives in the trash", listOf(null, null, 30L), steps.map { it.stamps.deletedAt })
        assertEquals(
            "a step made from a checklist line can be searched for",
            listOf("Door locked"),
            upgraded.taskDao().search("door*", 10).map { it.name },
        )
        assertEquals(AnujDatabase.VERSION, upgraded.openHelper.readableDatabase.version)
    }

    @Test
    fun `a version 4 database upgrades with its history kept and old history removable`() = runTest {
        createDatabaseAtVersion(4, "upgrade4.db") {
            it.execSQL(
                "INSERT INTO change_history (tableName, rowId, field, oldValue, newValue, changedAt) VALUES " +
                    "('task', 't', 'name', 'a', 'b', 10), ('task', 't', 'name', 'b', 'c', 50)",
            )
        }

        val upgraded = openCurrent("upgrade4.db")
        upgraded.changeHistoryDao().deleteBefore(20)

        assertEquals(listOf(50L), upgraded.snapshotDao().changeHistory().map { it.changedAt })
        assertEquals(AnujDatabase.VERSION, upgraded.openHelper.readableDatabase.version)
    }

    @Test
    fun `a version 5 database upgrades with its tasks kept and places working`() = runTest {
        createDatabaseAtVersion(5, "upgrade5.db") {
            it.execSQL(
                "INSERT INTO task (id, name, description, daysOff, priority, carryCount, createdAt, updatedAt) " +
                    "VALUES ('t', 'Buy the water can', '', 0, 'NONE', 0, 1, 1)",
            )
        }

        val upgraded = openCurrent("upgrade5.db")
        val places = upgraded.placeDao()
        places.upsert(PlaceEntity("shop", "Shop", 21.1, 79.0, 100, "SAVED", stamps = stamps))
        places.upsertVisit(PlaceVisitEntity("v", "shop", arrivedAt = 5))
        places.upsertTie(TaskPlaceEntity("t", "shop", "ARRIVE"))

        assertEquals(listOf("t"), places.tiesOf("shop").map { it.taskId })
        places.delete("shop")
        assertTrue("a place takes its visits and its ties with it", places.tiesOf("shop").isEmpty())
        assertEquals(0, places.countVisits("shop"))
        assertEquals(listOf("Buy the water can"), upgraded.snapshotDao().tasks().map { it.name })
        assertEquals(AnujDatabase.VERSION, upgraded.openHelper.readableDatabase.version)
    }

    @Test
    fun `a version 2 database upgrades with its tasks kept and reminders working`() = runTest {
        createDatabaseAtVersion(2, "upgrade2.db") {
            it.execSQL(
                "INSERT INTO task (id, name, description, daysOff, priority, carryCount, createdAt, updatedAt) " +
                    "VALUES ('t', 'Buy milk', '', 0, 'NONE', 0, 1, 1)",
            )
        }

        val upgraded = openCurrent("upgrade2.db")

        assertEquals(listOf("Buy milk"), upgraded.snapshotDao().tasks().map { it.name })
        upgraded.reminderDao().upsert(listOf(ReminderEntity(id = "r", taskId = "t", schedule = "TASK;0", stamps = stamps)))
        assertEquals(listOf("t"), upgraded.reminderDao().taskIdsEverReminded())
        assertEquals(AnujDatabase.VERSION, upgraded.openHelper.readableDatabase.version)
    }

    @Test
    fun `removing a task for good takes its reminders with it and leaves the standing ones`() = runTest {
        val database = openCurrent("cascade.db")
        database.taskDao().save(listOf(TaskEntity(id = "t", name = "Call mum", stamps = stamps)), emptyList())
        database.reminderDao().upsert(
            listOf(
                ReminderEntity(id = "of-task", taskId = "t", schedule = "TASK;0", stamps = stamps),
                ReminderEntity(id = "standing", title = "Drink water", schedule = "EVERY;120;540;1260;0", stamps = stamps),
            ),
        )

        database.snapshotDao().clearTasks()

        assertEquals(listOf("standing"), database.reminderDao().getLive().map { it.id })
    }

    @Test
    fun `a migrated database and a fresh one have the same tables and indexes`() = runTest {
        createDatabaseAtVersion(1, "migrated.db") {}
        val migrated = openCurrent("migrated.db")
        val fresh = Room.databaseBuilder(context, AnujDatabase::class.java, "fresh.db").allowMainThreadQueries().build()

        fun objects(database: AnujDatabase): Set<String> {
            val names = mutableSetOf<String>()
            database.openHelper.writableDatabase
                .query("SELECT type || ' ' || name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'")
                .use { cursor -> while (cursor.moveToNext()) names += cursor.getString(0) }
            return names
        }

        try {
            assertEquals(objects(fresh), objects(migrated))
        } finally {
            fresh.close()
        }
    }

    private fun fullSnapshot() = DatabaseSnapshot(
        schemaVersion = AnujDatabase.VERSION,
        /** The child is listed before its parent on purpose: a restore must not depend on row order. */
        tasks = listOf(
            TaskEntity(id = "child", parentId = "parent", name = "Pack charger", stamps = stamps),
            TaskEntity(
                id = "parent", name = "Trip", description = "to Pune", dueDay = 20_000, dueMinute = 600,
                repetition = "WEEKLY;1;MONDAY", daysOff = 96, priority = "HIGH", energy = "LOW",
                estimatedMinutes = 30, carryOver = "IN_DAYS;2", carryCount = 1, completedAt = 5, missedAt = null,
                stamps = StampColumns(1, 2, 3),
            ),
        ),
        notes = listOf(NoteEntity("n", "child", "the white one", stamps)),
        tags = listOf(TagEntity("home", "Home", 2, stamps)),
        taskTags = listOf(TaskTagEntity("parent", "home")),
        attachments = listOf(AttachmentEntity("a", "parent", "photo.jpg", stamps)),
        occurrences = listOf(TaskOccurrenceEntity("o", "parent", 19_999, "DONE", 77)),
        changeHistory = listOf(ChangeHistoryEntity(7, "task", "parent", "name", "Tripp", "Trip", 9)),
        reminders = listOf(
            ReminderEntity(
                id = "r", taskId = "parent", schedule = "TASK;15", category = "ALARM", style = "ALARM",
                nagEveryMinutes = 10, nagTimes = 3, toneUri = "content://tone", lastOccurrenceAt = 4, lastFiredAt = 5,
                nagsSent = 1, snoozedUntil = 6, answeredAt = 7, stamps = stamps,
            ),
            ReminderEntity(id = "water", title = "Drink water", schedule = "EVERY;120;540;1260;0", enabled = false, stamps = stamps),
        ),
        places = listOf(PlaceEntity("home", "Home", 21.1458, 79.0882, 100, "SAVED", isHome = true, stamps = stamps)),
        placeVisits = listOf(PlaceVisitEntity("v", "home", arrivedAt = 3, leftAt = 9)),
        taskPlaces = listOf(TaskPlaceEntity("parent", "home", "LEAVE")),
        reminderEvents = listOf(ReminderEventEntity("e", "r", "parent", "Trip", "SNOOZED", 8, minutes = 10, reason = "Busy")),
    )

    private fun DatabaseSnapshot.sorted() = copy(tasks = tasks.sortedBy { it.id }, reminders = reminders.sortedBy { it.id })

    @Test
    fun `a backup written to a file and restored into an empty database gives back every row`() = runTest {
        val original = fullSnapshot()
        val file = ByteArrayOutputStream().also { SnapshotFormat.write(original, it) }.toByteArray()

        val restored = openCurrent("restore.db")
        restored.snapshotDao().replaceWith(SnapshotFormat.read(ByteArrayInputStream(file)))

        assertEquals(original.sorted(), restored.snapshotDao().read(AnujDatabase.VERSION).sorted())
        /** The parent task in the backup is a trashed one, so search is checked through the child's note. */
        assertEquals(listOf("Pack charger"), restored.taskDao().search("white*", 10).map { it.name })
    }

    @Test
    fun `restoring replaces what was there instead of adding to it`() = runTest {
        val database = openCurrent("replace.db")
        database.taskDao().save(listOf(TaskEntity(id = "old", name = "Old task", stamps = stamps)), emptyList())
        database.reminderDao().upsert(listOf(ReminderEntity(id = "old-standing", title = "Old", schedule = "TIMES;480;0", stamps = stamps)))

        database.snapshotDao().replaceWith(fullSnapshot())

        val after = database.snapshotDao().read(AnujDatabase.VERSION)
        assertEquals(setOf("child", "parent"), after.tasks.map { it.id }.toSet())
        assertEquals("a standing reminder from before the restore must not survive it", setOf("r", "water"), after.reminders.map { it.id }.toSet())
        assertTrue("replaced text is gone from search", database.taskDao().search("old*", 10).isEmpty())
    }

    @Test
    fun `a backup that cannot be restored leaves the existing data untouched`() = runTest {
        val database = openCurrent("failed.db")
        database.taskDao().save(listOf(TaskEntity(id = "mine", name = "My task", stamps = stamps)), emptyList())
        val broken = fullSnapshot().copy(
            notes = listOf(NoteEntity("n", "no-such-task", "points at a task that is not in the backup", stamps)),
        )

        try {
            database.snapshotDao().replaceWith(broken)
            fail("a note for a missing task must not be accepted")
        } catch (expected: Exception) {
            assertEquals(listOf("mine"), database.snapshotDao().tasks().map { it.id })
        }
    }

    @Test
    fun `a backup from an older version, missing newer tables and columns, still reads`() {
        val old = """{"schemaVersion":1,"tasks":[{"id":"t","name":"Old","stamps":{"createdAt":1,"updatedAt":1}}],"futureTable":[1,2]}"""

        val snapshot = SnapshotFormat.read(ByteArrayInputStream(old.toByteArray()))

        assertEquals("Old", snapshot.tasks.single().name)
        assertEquals("NONE", snapshot.tasks.single().priority)
        assertTrue(snapshot.notes.isEmpty())
    }
}
