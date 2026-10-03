package com.karan.anuj.feature.task.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.karan.anuj.core.domain.task.Tag
import com.karan.anuj.core.domain.task.TagId
import com.karan.anuj.core.ui.components.AnujBottomSheet
import com.karan.anuj.core.ui.components.PrimaryButton
import com.karan.anuj.core.ui.components.SecondaryButton
import com.karan.anuj.feature.task.R

/**
 * Tags are picked by tapping chips. Making a new one is the only part that
 * needs words, and its colour is chosen from swatches.
 *
 * @param onCreate makes a tag and puts it on the task
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsSheet(
    allTags: List<Tag>,
    selected: Set<TagId>,
    onToggle: (TagId) -> Unit,
    onCreate: (name: String, colorIndex: Int) -> Unit,
    onRemoveTag: (Tag) -> Unit,
    onDismiss: () -> Unit,
) {
    var removing by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newColor by rememberSaveable { mutableIntStateOf(0) }
    val create = {
        if (newName.isNotBlank()) {
            onCreate(newName, newColor)
            newName = ""
        }
    }

    AnujBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.tags_title)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTags.forEach { tag ->
                    if (removing) {
                        InputChip(
                            selected = false,
                            onClick = { onRemoveTag(tag) },
                            label = { Text(tag.name) },
                            leadingIcon = { TagDot(tag.colorIndex) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.tags_remove, tag.name),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    } else {
                        FilterChip(
                            selected = tag.id in selected,
                            onClick = { onToggle(tag.id) },
                            label = { Text(tag.name) },
                            leadingIcon = { TagDot(tag.colorIndex) },
                        )
                    }
                }
            }
            if (allTags.isNotEmpty()) {
                TextButton(onClick = { removing = !removing }) {
                    Text(stringResource(if (removing) R.string.tags_manage_done else R.string.tags_manage))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.tags_new), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text(stringResource(R.string.tags_new_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TagPalette.colors.forEachIndexed { index, _ ->
                    val label = stringResource(R.string.tags_color, index + 1)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (index == newColor) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier,
                            )
                            .clickable { newColor = index }
                            .semantics { contentDescription = label },
                    ) {
                        TagDot(index, size = 28.dp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SecondaryButton(text = stringResource(R.string.task_add), onClick = create)
            Spacer(Modifier.height(8.dp))
            PrimaryButton(text = stringResource(R.string.task_ok), onClick = onDismiss)
        }
    }
}
