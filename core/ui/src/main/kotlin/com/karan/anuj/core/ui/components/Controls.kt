package com.karan.anuj.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The smallest height of anything meant to be tapped, sized for a thumb. */
val MinTouchTarget: Dp = 56.dp

/** The gap between chips, sideways and between wrapped rows. */
val ChipSpacing: Dp = 8.dp

/** How tall a chip is drawn: its whole height is the touch target. */
private val ChipHeight: Dp = 48.dp

/** The one main action of a screen or sheet. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** The quieter alternative next to a [PrimaryButton], such as "Not now". */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * A row of chips where exactly one is selected: the tap replacement for
 * typing a value or opening a dropdown. Wraps onto more lines when the
 * options do not fit, so it also works at the largest text size.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    /**
     * The chips fill their whole 48dp row, so without a gap between rows a
     * second line of chips would sit flush against the first and look overlapped.
     */
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        verticalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.titleSmall) },
                modifier = Modifier.heightIn(min = ChipHeight),
            )
        }
    }
}

/**
 * A row of chips where any number can be switched on: the tap replacement
 * for a list of checkboxes. Spaced and wrapped exactly like [ChoiceChips].
 *
 * @param centered lines the chips up in the middle, for a page whose text is centred
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ToggleChips(
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            ChipSpacing,
            if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ),
        verticalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = { Text(label(option), style = MaterialTheme.typography.titleSmall) },
                modifier = Modifier.heightIn(min = ChipHeight),
            )
        }
    }
}
