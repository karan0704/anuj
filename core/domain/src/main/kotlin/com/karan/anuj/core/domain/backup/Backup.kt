package com.karan.anuj.core.domain.backup

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

enum class BackupSchedule { OFF, DAILY, WEEKLY }

/**
 * @property folderUri the folder the user picked, as the address the system
 * file picker returned; null until one is chosen
 * @property folderName that folder's name, for showing
 * @property passwordSet whether backups are written password-protected
 * @property lastBackupAt when the last backup that worked was written
 * @property lastAttemptFailed true when the most recent attempt did not work
 */
data class BackupSettings(
    val folderUri: String? = null,
    val folderName: String? = null,
    val schedule: BackupSchedule = BackupSchedule.OFF,
    val passwordSet: Boolean = false,
    val lastBackupAt: Long? = null,
    val lastAttemptFailed: Boolean = false,
)

enum class BackupFailure {
    /** No backup folder has been chosen yet. */
    NO_FOLDER,

    /** The folder was removed, or the app's permission to it was taken away. */
    FOLDER_UNAVAILABLE,

    /** The backup is password-protected and no password was given. */
    PASSWORD_REQUIRED,
    WRONG_PASSWORD,

    /** The file is not an Anuj backup, or is damaged. */
    NOT_A_BACKUP,

    /** The backup was made by a newer version of the app than this one. */
    NEWER_VERSION,
    FAILED,
}

sealed interface BackupResult {
    data object Success : BackupResult
    data class Failure(val reason: BackupFailure) : BackupResult
}

interface BackupRepository {
    val settings: Flow<BackupSettings>

    /** Remembers the folder and keeps the permission to it across restarts. */
    suspend fun setFolder(folderUri: String)

    suspend fun setSchedule(schedule: BackupSchedule)

    /** @param password null or blank switches password protection off */
    suspend fun setPassword(password: String?)

    /** Writes a backup of everything into the chosen folder. */
    suspend fun backUpNow(): BackupResult

    /**
     * Replaces everything in the app with the contents of a backup file.
     * If the file cannot be used, nothing in the app is changed.
     */
    suspend fun restoreFrom(fileUri: String, password: String?): BackupResult
}

class BackupUseCase @Inject constructor(
    private val repository: BackupRepository,
) {
    fun observe(): Flow<BackupSettings> = repository.settings

    suspend fun setFolder(folderUri: String) = repository.setFolder(folderUri)

    suspend fun setSchedule(schedule: BackupSchedule) = repository.setSchedule(schedule)

    suspend fun setPassword(password: String?) = repository.setPassword(password?.takeIf { it.isNotBlank() })

    suspend fun backUpNow(): BackupResult = repository.backUpNow()

    suspend fun restoreFrom(fileUri: String, password: String?): BackupResult =
        repository.restoreFrom(fileUri, password?.takeIf { it.isNotEmpty() })
}
