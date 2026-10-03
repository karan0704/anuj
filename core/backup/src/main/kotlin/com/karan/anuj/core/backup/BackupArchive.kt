package com.karan.anuj.core.backup

import com.karan.anuj.core.data.db.DatabaseSnapshot
import com.karan.anuj.core.data.db.SnapshotFormat
import com.karan.anuj.core.domain.backup.BackupFailure
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod

/**
 * The small unencrypted label at the front of every backup.
 *
 * @property format the layout of the archive itself; raised only if the
 * entries inside change
 * @property schemaVersion the database version the rows came from
 */
@Serializable
data class BackupManifest(
    val format: Int = BackupArchive.FORMAT,
    val app: String = BackupArchive.APP,
    val createdAt: Long,
    val schemaVersion: Int,
)

class BackupContents(
    val manifest: BackupManifest,
    val snapshot: DatabaseSnapshot,
)

class BackupReadException(val reason: BackupFailure, cause: Throwable? = null) : Exception(reason.name, cause)

/**
 * A backup is an ordinary zip file, so it can be opened on a computer:
 *
 *     manifest.json     what this file is (never encrypted)
 *     data.json         every database row
 *     attachments/...   the photo files
 *
 * With a password, the data and the photos are AES-256 encrypted inside the
 * zip. The manifest stays readable so the app can recognise its own backup
 * and ask for the password instead of calling the file damaged.
 */
object BackupArchive {

    const val FORMAT = 1
    const val APP = "anuj"

    private const val MANIFEST = "manifest.json"
    private const val DATA = "data.json"
    private const val ATTACHMENTS = "attachments/"

    private val json = Json { ignoreUnknownKeys = true }

    fun write(
        output: OutputStream,
        password: CharArray?,
        manifest: BackupManifest,
        snapshot: DatabaseSnapshot,
        attachments: List<File>,
    ) {
        val zip = if (password != null) ZipOutputStream(output, password) else ZipOutputStream(output)
        zip.use {
            it.entry(MANIFEST, encrypt = false) { out -> out.write(json.encodeToString(manifest).toByteArray()) }
            it.entry(DATA, encrypt = password != null) { out -> SnapshotFormat.write(snapshot, out) }
            attachments.forEach { file ->
                it.entry(ATTACHMENTS + file.name, encrypt = password != null) { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
            }
        }
    }

    private inline fun ZipOutputStream.entry(name: String, encrypt: Boolean, write: (OutputStream) -> Unit) {
        putNextEntry(
            ZipParameters().apply {
                fileNameInZip = name
                if (encrypt) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            },
        )
        write(this)
        closeEntry()
    }

    /**
     * Reads a backup, writing its photo files into [attachmentsInto].
     *
     * @throws BackupReadException saying why the file cannot be used
     */
    fun read(input: InputStream, password: CharArray?, attachmentsInto: File): BackupContents {
        var manifest: BackupManifest? = null
        var snapshot: DatabaseSnapshot? = null

        try {
            ZipInputStream(input, password).use { zip ->
                while (true) {
                    val header = zip.nextEntry ?: break
                    val name = header.fileName
                    when {
                        name == MANIFEST -> manifest = json.decodeFromString<BackupManifest>(zip.readBytes().decodeToString())
                        name == DATA -> snapshot = SnapshotFormat.read(zip)
                        /** Only the file's own name is used, so an entry cannot write outside the target folder. */
                        name.startsWith(ATTACHMENTS) && !header.isDirectory ->
                            File(attachmentsInto, File(name).name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        } catch (failure: ZipException) {
            /**
             * The manifest comes first and is never encrypted, so a password
             * problem can only show up after it has been read. Anything
             * failing before that means the file is not one of ours.
             */
            val passwordProblem = manifest != null && failure.type == ZipException.Type.WRONG_PASSWORD
            throw BackupReadException(
                reason = when {
                    !passwordProblem -> BackupFailure.NOT_A_BACKUP
                    password == null -> BackupFailure.PASSWORD_REQUIRED
                    else -> BackupFailure.WRONG_PASSWORD
                },
                cause = failure,
            )
        } catch (failure: BackupReadException) {
            throw failure
        } catch (failure: Exception) {
            throw BackupReadException(BackupFailure.NOT_A_BACKUP, failure)
        }

        val readManifest = manifest
        val readSnapshot = snapshot
        if (readManifest == null || readSnapshot == null || readManifest.app != APP) {
            throw BackupReadException(BackupFailure.NOT_A_BACKUP)
        }
        if (readManifest.format > FORMAT) throw BackupReadException(BackupFailure.NEWER_VERSION)
        return BackupContents(readManifest, readSnapshot)
    }
}
