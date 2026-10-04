package com.karan.anuj.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karan.anuj.core.domain.backup.BackupFailure
import com.karan.anuj.core.domain.backup.BackupRepository
import com.karan.anuj.core.domain.backup.BackupResult
import com.karan.anuj.core.domain.backup.BackupSchedule
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Asks the system to run a backup every day or every week, including while
 * the app is closed and after the phone restarts. The system chooses the
 * exact moment, so it does not wake the phone just for this.
 */
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun apply(schedule: BackupSchedule) {
        val workManager = WorkManager.getInstance(context)
        val days = when (schedule) {
            BackupSchedule.OFF -> {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            BackupSchedule.DAILY -> 1L
            BackupSchedule.WEEKLY -> 7L
        }
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            /** Changing between daily and weekly replaces the existing schedule instead of adding a second one. */
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS).build(),
        )
    }

    private companion object {
        const val WORK_NAME = "anuj-scheduled-backup"
    }
}

/**
 * The scheduled run itself. It shows nothing to the user: a failure is
 * recorded in the backup settings and shown on the settings screen, so a
 * missed backup never becomes a notification.
 */
class BackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    /** The system creates workers itself, so the repository is fetched from Hilt rather than injected. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun backupRepository(): BackupRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .backupRepository()

        return when (val result = repository.backUpNow()) {
            BackupResult.Success -> Result.success()
            is BackupResult.Failure ->
                /** Only an unexpected error is worth another try soon; a missing folder will still be missing. */
                if (result.reason == BackupFailure.FAILED) Result.retry() else Result.failure()
        }
    }
}
