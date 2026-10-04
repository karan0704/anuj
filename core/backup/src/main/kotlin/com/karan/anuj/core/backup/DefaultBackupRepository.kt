package com.karan.anuj.core.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import com.karan.anuj.core.data.di.IoDispatcher
import com.karan.anuj.core.domain.backup.BackupFailure
import com.karan.anuj.core.domain.backup.BackupRepository
import com.karan.anuj.core.domain.backup.BackupResult
import com.karan.anuj.core.domain.backup.BackupSchedule
import com.karan.anuj.core.domain.backup.BackupSettings
import com.karan.anuj.core.domain.time.TimeSource
import com.karan.anuj.core.security.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.backupStore: DataStore<Preferences> by preferencesDataStore(name = "backup_preferences")

/**
 * Connects [BackupEngine] to the folder the user picked through the system
 * file picker. That folder can be on the phone or belong to a cloud app such
 * as Google Drive; the app only ever sees it as "a folder it may write to".
 */
@Singleton
class DefaultBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: BackupEngine,
    private val secrets: SecretStore,
    private val scheduler: BackupScheduler,
    private val time: TimeSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BackupRepository {

    override val settings: Flow<BackupSettings> = context.backupStore.data
        .map { prefs ->
            BackupSettings(
                folderUri = prefs[FOLDER_URI],
                folderName = prefs[FOLDER_NAME],
                schedule = BackupSchedule.entries.firstOrNull { it.name == prefs[SCHEDULE] } ?: BackupSchedule.OFF,
                passwordSet = prefs[PASSWORD_SET] ?: false,
                lastBackupAt = prefs[LAST_BACKUP_AT],
                lastAttemptFailed = prefs[LAST_ATTEMPT_FAILED] ?: false,
                keepCount = prefs[KEEP_COUNT] ?: BackupSettings.DEFAULT_KEEP_COUNT,
            )
        }
        .distinctUntilChanged()

    override suspend fun setFolder(folderUri: String) = withContext(io) {
        val uri = Uri.parse(folderUri)
        /** Without this the permission would be lost when the phone restarts and scheduled backups would stop. */
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val name = DocumentFile.fromTreeUri(context, uri)?.name
        context.backupStore.edit {
            it[FOLDER_URI] = folderUri
            if (name != null) it[FOLDER_NAME] = name else it.remove(FOLDER_NAME)
            it[LAST_ATTEMPT_FAILED] = false
        }
        Unit
    }

    override suspend fun setSchedule(schedule: BackupSchedule) {
        context.backupStore.edit { it[SCHEDULE] = schedule.name }
        scheduler.apply(schedule)
    }

    override suspend fun setPassword(password: String?) = withContext(io) {
        secrets.put(PASSWORD_SECRET, password)
        context.backupStore.edit { it[PASSWORD_SET] = password != null }
        Unit
    }

    override suspend fun setKeepCount(count: Int) {
        context.backupStore.edit { it[KEEP_COUNT] = count }
    }

    override suspend fun backUpNow(): BackupResult = withContext(io) {
        val result = writeBackup()
        context.backupStore.edit {
            it[LAST_ATTEMPT_FAILED] = result is BackupResult.Failure
            if (result is BackupResult.Success) it[LAST_BACKUP_AT] = time.nowMillis()
        }
        result
    }

    private suspend fun writeBackup(): BackupResult {
        val folderUri = settings.first().folderUri ?: return BackupResult.Failure(BackupFailure.NO_FOLDER)
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
            ?.takeIf { it.exists() && it.canWrite() }
            ?: return BackupResult.Failure(BackupFailure.FOLDER_UNAVAILABLE)

        val file = folder.createFile(MIME_ZIP, fileNameFor(time.nowMillis()))
            ?: return BackupResult.Failure(BackupFailure.FOLDER_UNAVAILABLE)
        return try {
            val output = context.contentResolver.openOutputStream(file.uri)
                ?: return BackupResult.Failure(BackupFailure.FOLDER_UNAVAILABLE)
            output.use { engine.writeTo(it, secrets.get(PASSWORD_SECRET)?.toCharArray()) }
            removeOldBackups(folder, keep = settings.first().keepCount)
            BackupResult.Success
        } catch (failure: Exception) {
            /** A backup that stopped half-way is worse than none: it looks usable and is not. */
            file.delete()
            BackupResult.Failure(BackupFailure.FAILED)
        }
    }

    override suspend fun restoreFrom(fileUri: String, password: String?): BackupResult = withContext(io) {
        val input = try {
            context.contentResolver.openInputStream(Uri.parse(fileUri))
        } catch (unreadable: Exception) {
            null
        } ?: return@withContext BackupResult.Failure(BackupFailure.NOT_A_BACKUP)

        val failure = input.use { engine.restoreFrom(it, password?.toCharArray()) }
        if (failure == null) BackupResult.Success else BackupResult.Failure(failure)
    }

    /**
     * Keeps the newest [keep] backups; the file names sort by date, so the
     * oldest are the first in name order. The number is the user's setting;
     * it used to be the fixed `BACKUPS_TO_KEEP = 10`.
     */
    private fun removeOldBackups(folder: DocumentFile, keep: Int) {
        folder.listFiles()
            .filter { it.isFile && it.name?.startsWith(FILE_PREFIX) == true }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { it.delete() }
    }

    private fun fileNameFor(millis: Long): String =
        FILE_PREFIX + FILE_STAMP.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())) + ".zip"

    private companion object {
        val FOLDER_URI = stringPreferencesKey("folder_uri")
        val FOLDER_NAME = stringPreferencesKey("folder_name")
        val SCHEDULE = stringPreferencesKey("schedule")
        val PASSWORD_SET = booleanPreferencesKey("password_set")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_ATTEMPT_FAILED = booleanPreferencesKey("last_attempt_failed")
        val KEEP_COUNT = intPreferencesKey("keep_count")

        const val PASSWORD_SECRET = "backup_password"
        const val MIME_ZIP = "application/zip"
        const val FILE_PREFIX = "anuj-backup-"
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
