package com.karan.anuj.feature.task.detail

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.karan.anuj.core.domain.task.Attachment
import com.karan.anuj.core.domain.task.CarryOverRule
import com.karan.anuj.core.domain.task.ChecklistItem
import com.karan.anuj.core.domain.task.Energy
import com.karan.anuj.core.domain.task.Note
import com.karan.anuj.core.domain.task.Priority
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.domain.task.TaskDetail
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.domain.task.TaskPreferences
import com.karan.anuj.core.ui.components.ChoiceChips
import com.karan.anuj.core.ui.components.FieldRow
import com.karan.anuj.core.ui.components.MinTouchTarget
import com.karan.anuj.core.ui.components.ScreenHeader
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.core.ui.components.SectionTitle
import com.karan.anuj.feature.task.R
import com.karan.anuj.feature.task.common.CarryOverSheet
import com.karan.anuj.feature.task.common.Day
import com.karan.anuj.feature.task.common.DueSheet
import com.karan.anuj.feature.task.common.RepeatSheet
import com.karan.anuj.feature.task.common.TagsSheet
import com.karan.anuj.feature.task.common.TaskRow
import com.karan.anuj.feature.task.common.TextEditSheet
import com.karan.anuj.feature.task.common.UndoSnackbars
import com.karan.anuj.feature.task.common.carryOverLabel
import com.karan.anuj.feature.task.common.dateLabel
import com.karan.anuj.feature.task.common.dueLabel
import com.karan.anuj.feature.task.common.energyLabel
import com.karan.anuj.feature.task.common.estimateLabel
import com.karan.anuj.feature.task.common.priorityLabel
import com.karan.anuj.feature.task.common.repetitionLabel
import com.karan.anuj.feature.task.quickadd.QuickAddSheet
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * The time estimates offered as chips come from the user's settings
 * ([TaskPreferences.estimateChoices]). They used to be fixed here:
 *
 *     private val EstimateChoices: List<Int?> = listOf(null, 5, 15, 30, 60, 120)
 */

/** Which sheet is open over the task. */
private enum class DetailSheet { NONE, NAME, DESCRIPTION, DUE, REPEAT, TAGS, CARRY, ADD_STEP, ADD_NOTE }

/**
 * One task with everything that belongs to it. Every value is changed by
 * tapping its row and choosing; only the name, description, notes, checklist
 * lines and new tag names are words, and each of those opens the keyboard
 * with its microphone available.
 *
 * @param onOpenTask opens another task: the parent, a step, or a fresh copy
 */
@Composable
fun TaskDetailScreen(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenTask: (TaskId) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDay()
        onPauseOrDispose {}
    }
    UndoSnackbars(viewModel.undoable, snackbar, viewModel::undo)
    RemovedPartSnackbars(viewModel, snackbar)

    Column(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            TaskDetailUiState.Loading -> DetailHeader(onBack = onBack)
            TaskDetailUiState.Gone -> {
                DetailHeader(onBack = onBack)
                Text(
                    stringResource(R.string.detail_gone),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ScreenPadding),
                )
            }
            is TaskDetailUiState.Loaded ->
                LoadedTask(current.detail, current.day, current.preferences, viewModel, onBack, onOpenTask)
        }
    }
}

@Composable
private fun RemovedPartSnackbars(viewModel: TaskDetailViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(viewModel, snackbar) {
        viewModel.removedParts.collect { part ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val result = snackbar.showSnackbar(
                    message = context.getString(R.string.task_removed_message, part.label),
                    actionLabel = context.getString(R.string.task_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restore(part)
            }
        }
    }
}

@Composable
private fun DetailHeader(
    onBack: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    ScreenHeader(
        title = stringResource(R.string.detail_title),
        onBack = onBack,
        backLabel = stringResource(R.string.task_back),
    ) {
        if (onDuplicate != null && onDelete != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.task_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.detail_duplicate)) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.detail_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedTask(
    detail: TaskDetail,
    day: Day,
    preferences: TaskPreferences,
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    onOpenTask: (TaskId) -> Unit,
) {
    val task = detail.task
    val today = day.date
    var sheet by rememberSaveable { mutableStateOf(DetailSheet.NONE) }
    /** The id of the photo shown large; kept as text so it survives rotation. */
    var openPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    val closeSheet = { sheet = DetailSheet.NONE }
    val copyName = stringResource(R.string.detail_copy_name, task.name)

    DetailHeader(
        onBack = onBack,
        onDuplicate = { viewModel.duplicate({ copyName }, onOpenTask) },
        /** No "Undo" message here: the screen closes with the task, and the trash is where it can be brought back from. */
        onDelete = { viewModel.moveToTrash(task, onDone = onBack) },
    )

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        detail.parent?.let { parent ->
            item(key = "parent") {
                TextButton(onClick = { onOpenTask(parent.id) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.detail_parent, parent.name))
                }
            }
        }

        item(key = "name") {
            NameRow(
                task = task,
                onToggleDone = { if (task.isOpen) viewModel.complete(task) else viewModel.reopen(task.id) },
                onEdit = { sheet = DetailSheet.NAME },
            )
        }

        item(key = "fields") {
            Column {
                FieldRow(
                    label = stringResource(R.string.detail_description),
                    value = task.description.ifBlank { stringResource(R.string.detail_description_empty) },
                    onClick = { sheet = DetailSheet.DESCRIPTION },
                )
                FieldRow(
                    label = stringResource(R.string.detail_when),
                    value = dueLabel(task.dueDate, task.dueTime, today) ?: stringResource(R.string.task_no_date),
                    onClick = { sheet = DetailSheet.DUE },
                )
                FieldRow(
                    label = stringResource(R.string.detail_repeat),
                    value = repetitionLabel(task.repetition, task.daysOff),
                    onClick = { sheet = DetailSheet.REPEAT },
                )
                ChipField(stringResource(R.string.detail_priority)) {
                    ChoiceChips(
                        options = Priority.entries,
                        selected = task.priority,
                        label = { priorityLabel(it) },
                        onSelect = { choice -> viewModel.update { it.copy(priority = choice) } },
                    )
                }
                FieldRow(
                    label = stringResource(R.string.detail_tags),
                    value = detail.allTags.filter { it.id in task.tagIds }.joinToString(", ") { it.name }
                        .ifEmpty { stringResource(R.string.detail_tags_none) },
                    onClick = { sheet = DetailSheet.TAGS },
                )
                ChipField(stringResource(R.string.detail_energy)) {
                    ChoiceChips(
                        options = listOf<Energy?>(null) + Energy.entries,
                        selected = task.energy,
                        label = { if (it == null) stringResource(R.string.task_none) else energyLabel(it) },
                        onSelect = { choice -> viewModel.update { it.copy(energy = choice) } },
                    )
                }
                ChipField(stringResource(R.string.detail_estimate)) {
                    ChoiceChips(
                        /** "None" first, then the user's lengths; a length no longer offered still shows while this task has it. */
                        options = (listOf<Int?>(null) + preferences.estimateChoices + task.estimatedMinutes).distinct(),
                        selected = task.estimatedMinutes,
                        label = { if (it == null) stringResource(R.string.task_none) else estimateLabel(it) },
                        onSelect = { choice -> viewModel.update { it.copy(estimatedMinutes = choice) } },
                    )
                }
                FieldRow(
                    label = stringResource(R.string.detail_carry),
                    value = task.carryOver?.let { carryOverLabel(it, today) } ?: inheritLabel(task, hasParent = detail.parent != null),
                    onClick = { sheet = DetailSheet.CARRY },
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
        }

        /**
         * A section's heading is only drawn once the section has something
         * in it. On a task with no checklist, steps, notes or photos the
         * headings used to sit above nothing; now only the "add" controls
         * show, each of which says what it adds. The four headings used to
         * be unconditional, each written like this one:
         *
         *     item(key = "steps-title") { SectionTitle(stringResource(R.string.detail_steps)) }
         */
        if (detail.checklist.isNotEmpty()) {
            item(key = "checklist-title") { SectionTitle(stringResource(R.string.detail_checklist)) }
        }
        items(detail.checklist, key = { "check-${it.id}" }) { line ->
            ChecklistRow(line, onChecked = { viewModel.setChecked(line, it) }, onRemove = { viewModel.removeChecklistLine(line) })
        }
        item(key = "checklist-add") { ChecklistAddRow(onAdd = viewModel::addChecklistLine) }

        if (detail.children.isNotEmpty()) {
            item(key = "steps-title") { SectionTitle(stringResource(R.string.detail_steps)) }
        }
        items(detail.children, key = { "step-${it.id.value}" }) { step ->
            TaskRow(
                task = step,
                today = today,
                progress = detail.childProgress[step.id],
                onToggleDone = { if (step.isOpen) viewModel.complete(step) else viewModel.reopen(step.id) },
                onClick = { onOpenTask(step.id) },
            )
        }
        item(key = "steps-add") {
            TextButton(onClick = { sheet = DetailSheet.ADD_STEP }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.detail_add_step))
            }
        }

        if (detail.notes.isNotEmpty()) {
            item(key = "notes-title") { SectionTitle(stringResource(R.string.detail_notes)) }
        }
        items(detail.notes, key = { "note-${it.id}" }) { note ->
            NoteRow(note, today = today, onRemove = { viewModel.removeNote(note) })
        }
        item(key = "notes-add") {
            TextButton(onClick = { sheet = DetailSheet.ADD_NOTE }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.detail_add_note))
            }
        }

        if (detail.attachments.isNotEmpty()) {
            item(key = "photos-title") { SectionTitle(stringResource(R.string.detail_photos)) }
        }
        item(key = "photos") {
            PhotoSection(
                photos = detail.attachments,
                pathOf = viewModel::photoPath,
                onOpen = { openPhotoId = it.id },
                viewModel = viewModel,
            )
        }
    }

    when (sheet) {
        DetailSheet.NONE -> Unit
        DetailSheet.NAME -> TextEditSheet(
            title = stringResource(R.string.detail_name),
            initial = task.name,
            hint = stringResource(R.string.quick_add_name),
            singleLine = true,
            onSave = { text -> viewModel.update { it.copy(name = text) } },
            onDismiss = closeSheet,
        )
        DetailSheet.DESCRIPTION -> TextEditSheet(
            title = stringResource(R.string.detail_description),
            initial = task.description,
            hint = stringResource(R.string.detail_description_empty),
            singleLine = false,
            onSave = { text -> viewModel.update { it.copy(description = text.trim()) } },
            onDismiss = closeSheet,
        )
        DetailSheet.DUE -> DueSheet(
            date = task.dueDate,
            time = task.dueTime,
            today = today,
            /** A repeating task always needs a day to repeat from. */
            allowNoDate = task.repetition == null,
            /** A day chosen here is a deliberate plan, so the missed mark and nothing else is cleared; the carry count stays. */
            onDateChange = { date -> viewModel.update { it.copy(dueDate = date, missedAt = null) } },
            onTimeChange = { time -> viewModel.update { it.copy(dueTime = time) } },
            onDismiss = closeSheet,
            dayParts = preferences.dayParts,
        )
        DetailSheet.REPEAT -> RepeatSheet(
            rule = task.repetition,
            daysOff = task.daysOff,
            anchor = task.dueDate ?: today,
            onChange = viewModel::setRepetition,
            onDismiss = closeSheet,
        )
        DetailSheet.TAGS -> TagsSheet(
            allTags = detail.allTags,
            selected = task.tagIds,
            onToggle = viewModel::toggleTag,
            onCreate = viewModel::createTag,
            onRemoveTag = viewModel::removeTag,
            onDismiss = closeSheet,
        )
        DetailSheet.CARRY -> CarryOverSheet(
            title = stringResource(R.string.detail_carry),
            rule = task.carryOver,
            inheritLabel = inheritLabel(task, hasParent = detail.parent != null),
            today = today,
            onChange = { rule: CarryOverRule? -> viewModel.update { it.copy(carryOver = rule) } },
            onDismiss = closeSheet,
        )
        DetailSheet.ADD_STEP -> QuickAddSheet(parentId = task.id, defaultDate = null, onDismiss = closeSheet)
        DetailSheet.ADD_NOTE -> TextEditSheet(
            title = stringResource(R.string.detail_add_note),
            initial = "",
            hint = stringResource(R.string.detail_note_hint),
            singleLine = false,
            onSave = viewModel::addNote,
            onDismiss = closeSheet,
        )
    }

    detail.attachments.firstOrNull { it.id == openPhotoId }?.let { photo ->
        val label = stringResource(R.string.detail_photo)
        PhotoDialog(
            path = viewModel.photoPath(photo),
            onRemove = {
                viewModel.removePhoto(photo, label)
                openPhotoId = null
            },
            onDismiss = { openPhotoId = null },
        )
    }
}

/** What "no rule of its own" means for this task, in words. */
@Composable
private fun inheritLabel(task: Task, hasParent: Boolean): String = stringResource(
    when {
        task.repetition != null -> R.string.carry_repeating_default
        hasParent -> R.string.carry_inherit
        else -> R.string.carry_inherit_default
    },
)

@Composable
private fun NameRow(task: Task, onToggleDone: () -> Unit, onEdit: () -> Unit) {
    val checkLabel = stringResource(R.string.task_mark_done, task.name)
    val editLabel = stringResource(R.string.detail_edit_name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(start = ScreenPadding - 12.dp, end = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = { onToggleDone() },
            modifier = Modifier.semantics { contentDescription = checkLabel },
        )
        Text(
            text = task.name,
            style = MaterialTheme.typography.headlineSmall,
            textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = editLabel, onClick = onEdit)
                .padding(vertical = 8.dp),
        )
    }
}

/** A label with a row of chips under it, lined up with the tappable field rows around it. */
@Composable
private fun ChipField(label: String, chips: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        chips()
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItem, onChecked: (Boolean) -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable { onChecked(!item.checked) }
            .padding(start = ScreenPadding - 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.detail_checklist_remove, item.text))
        }
    }
}

/** Stays focused after each line, so a whole checklist can be entered in one go. */
@Composable
private fun ChecklistAddRow(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.detail_checklist_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = add, enabled = text.isNotBlank()) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.task_add))
        }
    }
}

@Composable
private fun NoteRow(note: Note, today: LocalDate, onRemove: () -> Unit) {
    val written = Instant.ofEpochMilli(note.stamps.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(note.text, style = MaterialTheme.typography.bodyLarge)
            Text(dateLabel(written, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.detail_note_remove))
        }
    }
}

@Composable
private fun PhotoSection(
    photos: List<Attachment>,
    pathOf: (Attachment) -> String,
    onOpen: (Attachment) -> Unit,
    viewModel: TaskDetailViewModel,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPickedPhoto(it.toString()) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), viewModel::onCameraResult)

    Column {
        if (photos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(photos, key = { it.id }) { photo ->
                    AsyncImage(
                        model = File(pathOf(photo)),
                        contentDescription = stringResource(R.string.detail_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(photo) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.prepareCamera { uri ->
                        try {
                            camera.launch(Uri.parse(uri))
                        } catch (noCameraApp: ActivityNotFoundException) {
                            /** No app can take a photo on this phone; the reserved file is given back. */
                            viewModel.onCameraResult(photoTaken = false)
                        }
                    }
                },
            ) { Text(stringResource(R.string.detail_take_photo)) }
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) { Text(stringResource(R.string.detail_choose_photo)) }
        }
    }
}

@Composable
private fun PhotoDialog(path: String, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_photo_close)) } },
        dismissButton = { TextButton(onClick = onRemove) { Text(stringResource(R.string.task_remove)) } },
        text = {
            AsyncImage(
                model = File(path),
                contentDescription = stringResource(R.string.detail_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
            )
        },
    )
}
