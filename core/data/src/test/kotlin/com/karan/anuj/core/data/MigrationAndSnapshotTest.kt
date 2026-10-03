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
import com.karan.anuj.core.data.db.ChecklistItemEntity
import com.karan.anuj.core.data.db.DatabaseSnapshot
import com.karan.anuj.core.data.db.Migrations
import com.karan.anuj.core.data.db.NoteEntity
import com.karan.anuj.core.data.db.SnapshotFormat
import com.karan.anuj.core.data.db.StampColumns
import com.karan.anuj.core.data.db.TagEntity
import com.karan.anuj.core.data.db.TaskEntity
import com.karan.anuj.core.data.db.TaskOccurrenceEntity
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
        checklistItems = listOf(ChecklistItemEntity("c", "parent", "passport", checked = true, position = 0, stamps = stamps)),
        notes = listOf(NoteEntity("n", "child", "the white one", stamps)),
        tags = listOf(TagEntity("home", "Home", 2, stamps)),
        taskTags = listOf(TaskTagEntity("parent", "home")),
        attachments = listOf(AttachmentEntity("a", "parent", "photo.jpg", stamps)),
        occurrences = listOf(TaskOccurrenceEntity("o", "parent", 19_999, "DONE", 77)),
        changeHistory = listOf(ChangeHistoryEntity(7, "task", "parent", "name", "Tripp", "Trip", 9)),
    )

    private fun DatabaseSnapshot.sorted() = copy(tasks = tasks.sortedBy { it.id })

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
        database.checklistDao().upsert(listOf(ChecklistItemEntity("oc", "old", "old line", stamps = stamps)))

        database.snapshotDao().replaceWith(fullSnapshot())

        val after = database.snapshotDao().read(AnujDatabase.VERSION)
        assertEquals(setOf("child", "parent"), after.tasks.map { it.id }.toSet())
        assertEquals(listOf("c"), after.checklistItems.map { it.id })
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
