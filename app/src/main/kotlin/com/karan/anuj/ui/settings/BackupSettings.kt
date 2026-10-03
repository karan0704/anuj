package com.karan.anuj.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karan.anuj.R
import com.karan.anuj.core.domain.backup.BackupFailure
import com.karan.anuj.core.domain.backup.BackupResult
import com.karan.anuj.core.domain.backup.BackupSchedule
import com.karan.anuj.core.domain.backup.BackupSettings
import com.karan.anuj.core.domain.backup.BackupUseCase
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.core.ui.components.Stepper
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What to tell the user after a backup or restore, as a string resource so the wording lives with the other labels. */
enum class BackupMessage(@StringRes val text: Int) {
    BACKED_UP(R.string.backup_message_done),
    RESTORED(R.string.backup_message_restored),
    NO_FOLDER(R.string.backup_message_no_folder),
    FOLDER_UNAVAILABLE(R.string.backup_message_folder_unavailable),
    WRONG_PASSWORD(R.string.backup_message_wrong_password),
    NOT_A_BACKUP(R.string.backup_message_not_a_backup),
    NEWER_VERSION(R.string.backup_message_newer),
    FAILED(R.string.backup_message_failed),
}

/**
 * @property busy a backup or restore is running
 * @property message the outcome to show, until dismissed
 * @property passwordNeededFor the backup file a restore is waiting on a password for
 */
data class BackupUiState(
    val settings: BackupSettings = BackupSettings(),
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    val passwordNeededFor: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupUseCase,
) : ViewModel() {

    private data class Progress(
        val busy: Boolean = false,
        val message: BackupMessage? = null,
        val passwordNeededFor: String? = null,
    )

    private val progress = MutableStateFlow(Progress())

    val state: StateFlow<BackupUiState> =
        combine(backup.observe(), progress) { settings, now ->
            BackupUiState(settings, now.busy, now.message, now.passwordNeededFor)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun setFolder(folderUri: String) {
        viewModelScope.launch { backup.setFolder(folderUri) }
    }

    fun setSchedule(schedule: BackupSchedule) {
        viewModelScope.launch { backup.setSchedule(schedule) }
    }

    fun setPassword(password: String?) {
        viewModelScope.launch { backup.setPassword(password) }
    }

    fun setKeepCount(count: Int) {
        viewModelScope.launch { backup.setKeepCount(count) }
    }

    fun backUpNow() = run(onSuccess = BackupMessage.BACKED_UP, fileUri = null) { backup.backUpNow() }

    fun restore(fileUri: String, password: String?) =
        run(onSuccess = BackupMessage.RESTORED, fileUri = fileUri) { backup.restoreFrom(fileUri, password) }

    fun dismissMessage() = progress.update { it.copy(message = null) }

    fun cancelPassword() = progress.update { it.copy(passwordNeededFor = null) }

    /** Runs one backup or restore at a time and turns its outcome into what the screen should show. */
    private fun run(onSuccess: BackupMessage, fileUri: String?, action: suspend () -> BackupResult) {
        if (progress.value.busy) return
        progress.value = Progress(busy = true)
        viewModelScope.launch {
            progress.value = when (val result = action()) {
                BackupResult.Success -> Progress(message = onSuccess)
                is BackupResult.Failure -> when (result.reason) {
                    /** Not an error to report: the screen asks for the password and tries again. */
                    BackupFailure.PASSWORD_REQUIRED -> Progress(passwordNeededFor = fileUri)
                    /** A wrong guess keeps the password box open, with the reason shown. */
                    BackupFailure.WRONG_PASSWORD -> Progress(message = BackupMessage.WRONG_PASSWORD, passwordNeededFor = fileUri)
                    BackupFailure.NO_FOLDER -> Progress(message = BackupMessage.NO_FOLDER)
                    BackupFailure.FOLDER_UNAVAILABLE -> Progress(message = BackupMessage.FOLDER_UNAVAILABLE)
                    BackupFailure.NOT_A_BACKUP -> Progress(message = BackupMessage.NOT_A_BACKUP)
                    BackupFailure.NEWER_VERSION -> Progress(message = BackupMessage.NEWER_VERSION)
                    BackupFailure.FAILED -> Progress(message = BackupMessage.FAILED)
                }
            }
        }
    }
}

private val LastBackupFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

@StringRes
private fun BackupSchedule.labelRes(): Int = when (this) {
    BackupSchedule.OFF -> R.string.backup_schedule_off
    BackupSchedule.DAILY -> R.string.backup_schedule_daily
    BackupSchedule.WEEKLY -> R.string.backup_schedule_weekly
}

private enum class BackupSheet { NONE, SCHEDULE, PASSWORD, KEEP }

/**
 * The backup rows of the Settings screen. The folder and the file to restore
 * are chosen with the phone's own file picker, so the app needs no storage
 * permission and the folder can just as well be one in Google Drive.
 */
@Composable
fun BackupSettingsRows(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    var sheet by rememberSaveable { mutableStateOf(BackupSheet.NONE) }
    /** The file picked for a restore, waiting for "Replace everything?" to be answered. */
    var confirmRestoreOf by rememberSaveable { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.setFolder(it.toString()) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmRestoreOf = uri?.toString()
    }

    FieldRow(
        label = stringResource(R.string.backup_folder),
        value = settings.folderName ?: stringResource(R.string.backup_folder_none),
        onClick = { folderPicker.launch(null) },
    )
    FieldRow(
        label = stringResource(R.string.backup_schedule),
        value = stringResource(settings.schedule.labelRes()),
        onClick = { sheet = BackupSheet.SCHEDULE },
    )
    FieldRow(
        label = stringResource(R.string.backup_password),
        value = stringResource(if (settings.passwordSet) R.string.backup_password_set else R.string.backup_password_none),
        onClick = { sheet = BackupSheet.PASSWORD },
    )
    FieldRow(
        label = stringResource(R.string.backup_keep),
        value = stringResource(R.string.backup_keep_value, settings.keepCount),
        onClick = { sheet = BackupSheet.KEEP },
    )
    FieldRow(
        label = stringResource(R.string.backup_now),
        value = when {
            state.busy -> stringResource(R.string.backup_now_working)
            settings.lastAttemptFailed -> stringResource(R.string.backup_now_failed)
            settings.lastBackupAt != null -> stringResource(
                R.string.backup_now_last,
                Instant.ofEpochMilli(settings.lastBackupAt!!).atZone(ZoneId.systemDefault()).format(LastBackupFormat),
            )
            else -> stringResource(R.string.backup_now_never)
        },
        onClick = viewModel::backUpNow,
    )
    FieldRow(
        label = stringResource(R.string.backup_restore),
        value = stringResource(R.string.backup_restore_hint),
        /** Any file type is offered, because cloud folders do not always report a backup as a zip. */
        onClick = { filePicker.launch(arrayOf("*/*")) },
    )

    when (sheet) {
        BackupSheet.NONE -> Unit
        BackupSheet.SCHEDULE -> AnujBottomSheet(
            onDismiss = { sheet = BackupSheet.NONE },
            title = stringResource(R.string.backup_schedule),
        ) {
            ChoiceChips(
                options = BackupSchedule.entries,
                selected = settings.schedule,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setSchedule,
            )
        }
        BackupSheet.PASSWORD -> PasswordSheet(
            title = stringResource(R.string.backup_password),
            warning = stringResource(R.string.backup_password_warning),
            saveLabel = stringResource(R.string.backup_password_save),
            onSave = {
                viewModel.setPassword(it)
                sheet = BackupSheet.NONE
            },
            removeLabel = stringResource(R.string.backup_password_remove).takeIf { settings.passwordSet },
            onRemove = {
                viewModel.setPassword(null)
                sheet = BackupSheet.NONE
            },
            onDismiss = { sheet = BackupSheet.NONE },
        )
        BackupSheet.KEEP -> AnujBottomSheet(
            onDismiss = { sheet = BackupSheet.NONE },
            title = stringResource(R.string.backup_keep),
        ) {
            Stepper(
                value = settings.keepCount,
                onChange = viewModel::setKeepCount,
                range = BackupSettings.KEEP_COUNT_RANGE,
                lessLabel = stringResource(R.string.backup_keep_less),
                moreLabel = stringResource(R.string.backup_keep_more),
                valueText = stringResource(R.string.backup_keep_value, settings.keepCount),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.backup_ok), onClick = { sheet = BackupSheet.NONE })
        }
    }

    confirmRestoreOf?.let { fileUri ->
        AlertDialog(
            onDismissRequest = { confirmRestoreOf = null },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestoreOf = null
                        viewModel.restore(fileUri, password = null)
                    },
                ) { Text(stringResource(R.string.backup_restore_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreOf = null }) { Text(stringResource(R.string.backup_cancel)) }
            },
        )
    }

    state.passwordNeededFor?.let { fileUri ->
        PasswordSheet(
            title = stringResource(R.string.backup_restore_password_title),
            warning = state.message?.let { stringResource(it.text) },
            saveLabel = stringResource(R.string.backup_restore_confirm),
            onSave = { viewModel.restore(fileUri, it) },
            removeLabel = null,
            onRemove = {},
            onDismiss = viewModel::cancelPassword,
        )
    }

    /** While the password box is open it shows the message itself, so the dialog would only repeat it. */
    val message = state.message
    if (message != null && state.passwordNeededFor == null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(stringResource(message.text)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.backup_ok)) }
            },
        )
    }
}

/**
 * The one place in the app where typing cannot be avoided: a password. It is
 * optional, and never asked for again once saved.
 *
 * @param removeLabel shown as a second button when there is a password to remove
 */
@Composable
private fun PasswordSheet(
    title: String,
    warning: String?,
    saveLabel: String,
    onSave: (String) -> Unit,
    removeLabel: String?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }

    AnujBottomSheet(onDismiss = onDismiss, title = title) {
        if (warning != null) {
            Text(warning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text(stringResource(R.string.backup_password_hint)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = saveLabel, onClick = { onSave(password) }, enabled = password.isNotEmpty())
        if (removeLabel != null) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = removeLabel, onClick = onRemove)
        }
    }
}
