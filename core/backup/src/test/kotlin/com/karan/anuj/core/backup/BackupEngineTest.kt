package com.karan.anuj.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.karan.anuj.core.data.db.AnujDatabase
import com.karan.anuj.core.data.db.AttachmentEntity
import com.karan.anuj.core.data.db.ChecklistItemEntity
import com.karan.anuj.core.data.db.DatabaseSnapshot
import com.karan.anuj.core.data.db.StampColumns
import com.karan.anuj.core.data.db.TaskEntity
import com.karan.anuj.core.data.task.AttachmentDirectory
import com.karan.anuj.core.domain.backup.BackupFailure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Backs up a real database and real files, wipes or changes them, restores,
 * and checks what came back. Restore is the part that must not lose data,
 * so each way it can fail is checked to leave everything as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnujDatabase
    private lateinit var engine: BackupEngine
    private lateinit var photos: File

    private val stamps = StampColumns(createdAt = 1, updatedAt = 2, deletedAt = null)
    private val photoBytes = ByteArray(5_000) { (it % 251).toByte() }

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AnujDatabase::class.java).allowMainThreadQueries().build()
        val directory = AttachmentDirectory(context)
        photos = directory.folder
        engine = BackupEngine({ db.snapshotDao() }, directory, context, time = { 1_000L }, io = Dispatchers.Unconfined)
    }

    @After
    fun close() {
        db.close()
        photos.deleteRecursively()
    }

    private suspend fun fill() {
        db.taskDao().save(
            listOf(
                TaskEntity(id = "trip", name = "Trip to Pune", stamps = stamps),
                TaskEntity(id = "pack", parentId = "trip", name = "Pack bag", stamps = stamps),
            ),
            emptyList(),
        )
        db.checklistDao().upsert(listOf(ChecklistItemEntity("c", "pack", "passport", stamps = stamps)))
        db.attachmentDao().upsert(AttachmentEntity("a", "trip", "ticket.jpg", stamps))
        File(photos, "ticket.jpg").writeBytes(photoBytes)
        /** A file the database does not know about, which a backup should leave behind. */
        File(photos, "stray.jpg").writeBytes(byteArrayOf(1, 2, 3))
    }

    private suspend fun backup(password: String? = null): ByteArray =
        ByteArrayOutputStream().also { engine.writeTo(it, password?.toCharArray()) }.toByteArray()

    private suspend fun restore(file: ByteArray, password: String? = null): BackupFailure? =
        engine.restoreFrom(ByteArrayInputStream(file), password?.toCharArray())

    private suspend fun wipe() {
        db.snapshotDao().replaceWith(DatabaseSnapshot(schemaVersion = AnujDatabase.VERSION))
        photos.listFiles()?.forEach { it.delete() }
    }

    private suspend fun taskNames() = db.snapshotDao().tasks().map { it.name }.toSet()

    @Test
    fun `everything backed up comes back after the app's data is wiped`() = runTest {
        fill()
        val before = db.snapshotDao().read(AnujDatabase.VERSION)
        val file = backup()

        wipe()
        assertTrue(taskNames().isEmpty())

        assertNull(restore(file))
        assertEquals(before, db.snapshotDao().read(AnujDatabase.VERSION))
        assertArrayEquals(photoBytes, File(photos, "ticket.jpg").readBytes())
        assertEquals(listOf("Pack bag"), db.taskDao().search("passport*", 10).map { it.name })
    }

    @Test
    fun `restoring replaces newer data and photos with what the backup holds`() = runTest {
        fill()
        val file = backup()
        db.taskDao().save(listOf(TaskEntity(id = "later", name = "Added later", stamps = stamps)), emptyList())
        File(photos, "later.jpg").writeBytes(byteArrayOf(9))

        assertNull(restore(file))

        assertEquals(setOf("Trip to Pune", "Pack bag"), taskNames())
        assertFalse(File(photos, "later.jpg").exists())
    }

    @Test
    fun `a photo file the database does not know about is not put in the backup`() = runTest {
        fill()
        val file = backup()
        wipe()

        restore(file)

        assertEquals(listOf("ticket.jpg"), photos.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `a password-protected backup restores with the right password`() = runTest {
        fill()
        val file = backup(password = "s3cret")
        wipe()

        assertNull(restore(file, password = "s3cret"))

        assertEquals(setOf("Trip to Pune", "Pack bag"), taskNames())
        assertArrayEquals(photoBytes, File(photos, "ticket.jpg").readBytes())
    }

    @Test
    fun `a password-protected backup does not contain readable task text`() = runTest {
        fill()

        val protected = backup(password = "s3cret").toString(Charsets.ISO_8859_1)

        assertFalse(protected.contains("Pune"))
        assertFalse(protected.contains("passport"))
    }

    @Test
    fun `without the password the app asks for one and changes nothing`() = runTest {
        fill()
        val file = backup(password = "s3cret")
        db.taskDao().save(listOf(TaskEntity(id = "later", name = "Added later", stamps = stamps)), emptyList())

        assertEquals(BackupFailure.PASSWORD_REQUIRED, restore(file))
        assertEquals(setOf("Trip to Pune", "Pack bag", "Added later"), taskNames())
    }

    @Test
    fun `a wrong password is refused and changes nothing`() = runTest {
        fill()
        val file = backup(password = "s3cret")

        assertEquals(BackupFailure.WRONG_PASSWORD, restore(file, password = "guess"))
        assertEquals(setOf("Trip to Pune", "Pack bag"), taskNames())
        assertArrayEquals(photoBytes, File(photos, "ticket.jpg").readBytes())
    }

    @Test
    fun `a file that is not a backup is refused and changes nothing`() = runTest {
        fill()

        assertEquals(BackupFailure.NOT_A_BACKUP, restore("just some text, not a zip".toByteArray()))
        assertEquals(BackupFailure.NOT_A_BACKUP, restore(ByteArray(0)))
        assertEquals(setOf("Trip to Pune", "Pack bag"), taskNames())
    }

    @Test
    fun `a backup cut short is refused and changes nothing`() = runTest {
        fill()
        val file = backup()

        val failure = restore(file.copyOf(file.size / 2))

        assertEquals(BackupFailure.NOT_A_BACKUP, failure)
        assertEquals(setOf("Trip to Pune", "Pack bag"), taskNames())
        assertTrue(File(photos, "ticket.jpg").exists())
    }

    @Test
    fun `a backup from a newer version of the app is refused and changes nothing`() = runTest {
        fill()
        val fromTheFuture = ByteArrayOutputStream().also {
            BackupArchive.write(
                output = it,
                password = null,
                manifest = BackupManifest(createdAt = 1, schemaVersion = AnujDatabase.VERSION + 1),
                snapshot = DatabaseSnapshot(schemaVersion = AnujDatabase.VERSION + 1),
                attachments = emptyList(),
            )
        }.toByteArray()

        assertEquals(BackupFailure.NEWER_VERSION, restore(fromTheFuture))
        assertEquals(setOf("Trip to Pune", "Pack bag"), taskNames())
    }

    @Test
    fun `restoring leaves no temporary files behind`() = runTest {
        fill()
        val file = backup()

        restore(file)
        restore("not a zip".toByteArray())

        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("restore-") })
    }
}
