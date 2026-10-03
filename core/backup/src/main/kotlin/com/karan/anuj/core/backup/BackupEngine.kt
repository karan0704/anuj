package com.karan.anuj.core.backup

import android.content.Context
import com.karan.anuj.core.data.db.AnujDatabase
import com.karan.anuj.core.data.db.SnapshotDao
import com.karan.anuj.core.data.di.IoDispatcher
import com.karan.anuj.core.data.task.AttachmentDirectory
import com.karan.anuj.core.domain.backup.BackupFailure
import com.karan.anuj.core.domain.time.TimeSource
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Turns the app's data into a backup and back. It works on plain streams and
 * knows nothing about folders or the file picker, which keeps the part that
 * must not lose data small enough to test completely.
 */
class BackupEngine @Inject constructor(
    private val snapshots: Lazy<SnapshotDao>,
    private val attachments: AttachmentDirectory,
    @ApplicationContext private val context: Context,
    private val time: TimeSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun writeTo(output: OutputStream, password: CharArray?) = withContext(io) {
        val snapshot = snapshots.get().read(AnujDatabase.VERSION)
        /** Only photos the database still knows about are copied, so stray files do not grow the backup. */
        val known = snapshot.attachments.mapTo(HashSet()) { it.fileName }
        val files = attachments.folder.listFiles().orEmpty().filter { it.isFile && it.name in known }

        BackupArchive.write(
            output = output,
            password = password,
            manifest = BackupManifest(createdAt = time.nowMillis(), schemaVersion = AnujDatabase.VERSION),
            snapshot = snapshot,
            attachments = files,
        )
    }

    /**
     * @return null when the restore worked, otherwise why it did not. On any
     * failure the app's data is exactly as it was.
     */
    suspend fun restoreFrom(input: InputStream, password: CharArray?): BackupFailure? = withContext(io) {
        /** Photos are unpacked to a staging folder first; the real one is only touched once the rows are in. */
        val staging = File(context.cacheDir, "restore-${time.nowMillis()}").apply { mkdirs() }
        try {
            val contents = try {
                BackupArchive.read(input, password, staging)
            } catch (unusable: BackupReadException) {
                return@withContext unusable.reason
            }
            if (contents.manifest.schemaVersion > AnujDatabase.VERSION) return@withContext BackupFailure.NEWER_VERSION

            try {
                snapshots.get().replaceWith(contents.snapshot)
            } catch (rejected: Exception) {
                /** The database refused the rows (they do not fit together); the transaction left the old data in place. */
                return@withContext BackupFailure.NOT_A_BACKUP
            }

            val folder = attachments.folder
            folder.listFiles()?.forEach { it.delete() }
            staging.listFiles()?.forEach { it.copyTo(File(folder, it.name), overwrite = true) }
            null
        } finally {
            staging.deleteRecursively()
        }
    }
}
