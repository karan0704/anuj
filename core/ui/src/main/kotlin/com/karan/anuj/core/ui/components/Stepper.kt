package com.karan.anuj.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A number changed by tapping down and up arrows instead of typing it.
 *
 * @param lessLabel read aloud for the down arrow
 * @param moreLabel read aloud for the up arrow
 */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    lessLabel: String,
    moreLabel: String,
    modifier: Modifier = Modifier,
    valueText: String = value.toString(),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = value > range.first,
            modifier = Modifier.size(MinTouchTarget),
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = lessLabel)
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 48.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
            modifier = Modifier.size(MinTouchTarget),
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = moreLabel)
        }
    }
}
