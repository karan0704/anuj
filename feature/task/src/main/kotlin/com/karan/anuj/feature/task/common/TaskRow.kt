package com.karan.anuj.feature.task.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karan.anuj.core.domain.task.ChildProgress
import com.karan.anuj.core.domain.task.Priority
import com.karan.anuj.core.domain.task.Tag
import com.karan.anuj.core.domain.task.TagId
import com.karan.anuj.core.domain.task.Task
import com.karan.anuj.core.ui.components.MinTouchTarget
import com.karan.anuj.core.ui.components.ScreenPadding
import com.karan.anuj.feature.task.R
import java.time.LocalDate

/** How far each level of the tree is pushed in. */
private val IndentStep: Dp = 20.dp

/** The repeat mark in front of a routine's details, sized to sit in a line of small text. */
private val KindMarkSize: Dp = 14.dp

/** A checkbox has 12dp of its own space around the box, so rows start that much left of the screen padding. */
private val CheckboxInset: Dp = 12.dp

/**
 * One task in any list.
 *
 * The tick box completes the task; tapping anywhere else opens it. Under the
 * name is one quiet line of whatever is set: when it is due, how it repeats,
 * step progress, priority, how often it has been carried. A routine says so
 * at the start of that line, with the repeat mark, so it is never mistaken
 * for a task that is done once.
 *
 * @param tags all tags by id, for drawing this task's coloured dots; null hides them
 * @param depth 0 for a top-level task; pushes the row in for the tree
 * @param trailing extra control at the right edge, such as the tree's expand arrow
 */
@Composable
fun TaskRow(
    task: Task,
    today: LocalDate,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tags: Map<TagId, Tag>? = null,
    progress: ChildProgress? = null,
    depth: Int = 0,
    trailing: @Composable (() -> Unit)? = null,
) {
    val checkLabel = stringResource(R.string.task_mark_done, task.name)
    val overdue = task.isOpen && task.dueDate != null && task.dueDate!! < today

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(start = ScreenPadding - CheckboxInset + IndentStep * depth, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = { onToggleDone() },
            modifier = Modifier.semantics { contentDescription = checkLabel },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isOpen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = taskDetailsLine(task, today, progress)
            if (details.isNotEmpty()) {
                val detailColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.repetition != null) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = detailColor,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(KindMarkSize),
                        )
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = detailColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val taskTags = tags?.let { all -> task.tagIds.mapNotNull(all::get) }.orEmpty()
            if (taskTags.isNotEmpty()) TagDots(taskTags)
        }
        trailing?.invoke()
    }
}

@Composable
private fun taskDetailsLine(task: Task, today: LocalDate, progress: ChildProgress?): String {
    val parts = buildList {
        if (task.repetition != null) add(stringResource(R.string.kind_routine))
        if (task.isMissed) add(stringResource(R.string.task_missed))
        dueLabel(task.dueDate, task.dueTime, today)?.let(::add)
        if (task.repetition != null) add(repetitionLabel(task.repetition, task.daysOff))
        if (progress != null && progress.total > 0) add(stringResource(R.string.task_progress, progress.done, progress.total))
        if (task.priority != Priority.NONE) add(priorityLabel(task.priority))
        task.estimatedMinutes?.let { add(estimateLabel(it)) }
        if (task.carryCount > 0) add(stringResource(R.string.task_carried, task.carryCount))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun TagDots(tags: List<Tag>) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tag ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagDot(tag.colorIndex)
                Spacer(Modifier.size(4.dp))
                Text(tag.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TagDot(colorIndex: Int, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(TagPalette.colorOf(colorIndex)),
    )
}

/**
 * Wraps a row so it can be swiped: to the right for [onSwipeRight], to the
 * left for [onSwipeLeft]. The row always slides back into place; it leaves
 * the list when the data behind it changes, which keeps a swipe and its
 * "Undo" from fighting over the row.
 *
 * @param onSwipeLeft null switches the left swipe off
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActions(
    rightLabel: String,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    leftLabel: String? = null,
    onSwipeLeft: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    /** The swipe state outlives recompositions, so it reads the callbacks through these to always call the latest ones. */
    val currentRight by rememberUpdatedState(onSwipeRight)
    val currentLeft by rememberUpdatedState(onSwipeLeft)

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentRight()
                SwipeToDismissBoxValue.EndToStart -> currentLeft?.invoke()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    /**
     * For someone using a screen reader a swipe is not discoverable, so the
     * same two actions are offered as named actions on the row, and the
     * labels painted behind the row are kept out of what is read aloud.
     */
    val rowActions = buildList {
        add(CustomAccessibilityAction(rightLabel) { currentRight(); true })
        if (leftLabel != null && onSwipeLeft != null) add(CustomAccessibilityAction(leftLabel) { currentLeft?.invoke(); true })
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics { customActions = rowActions },
        enableDismissFromEndToStart = onSwipeLeft != null,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = ScreenPadding)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(rightLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(leftLabel.orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}
