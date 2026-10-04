package com.karan.anuj.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Which thumb the app is laid out for. */
enum class Hand(val corner: Alignment, val side: Arrangement.Horizontal) {
    RIGHT(Alignment.BottomEnd, Arrangement.End),
    LEFT(Alignment.BottomStart, Arrangement.Start),
    ;

    companion object {
        /** The saved choice arrives by name, because this module does not know the settings. */
        fun named(name: String?): Hand = entries.firstOrNull { it.name == name } ?: RIGHT
    }
}

/**
 * How the screens are arranged for use with one hand.
 *
 * @property lowerLists a list starts part of the way down the screen, where
 * the thumb is, and rises to the top as it is scrolled
 */
data class OneHand(val hand: Hand = Hand.RIGHT, val lowerLists: Boolean = true)

/** Supplied once by the app shell, from the user's settings. */
val LocalOneHand = staticCompositionLocalOf { OneHand() }

/** How much of the screen's height a list is lowered by before it is scrolled. */
private const val REACH_FRACTION = 0.22f

/**
 * A tab's main list, arranged so the things that are tapped come to the
 * thumb and the things that are only read stay where they are.
 *
 * [top] is read-only (the clock, a title) and never moves. Under it the list
 * starts lowered; [actions] is the first thing in the list, so search and the
 * menu sit beside the thumb at rest. Scrolling carries the list and its
 * actions up together until the actions dock under [top], where they stay
 * while the rows pass beneath.
 *
 * @param bottomSpace room after the last row, so it can scroll clear of an add button
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OneHandList(
    top: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    bottomSpace: Dp = 96.dp,
    content: LazyListScope.() -> Unit,
) {
    val oneHand = LocalOneHand.current
    val reach = LocalConfiguration.current.screenHeightDp.dp * REACH_FRACTION

    Column(modifier = modifier.fillMaxSize()) {
        top()
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomSpace)) {
            if (oneHand.lowerLists) item(key = "reach") { Spacer(Modifier.height(reach)) }
            if (actions != null) stickyHeader(key = "actions") { ThumbActions(content = actions) }
            content()
        }
    }
}

/** The row of icon buttons that rides at the head of a list, on the side of the thumb. */
@Composable
fun ThumbActions(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            /** Opaque, so rows scrolling beneath the docked actions do not show through. */
            .background(MaterialTheme.colorScheme.background)
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = 12.dp),
        horizontalArrangement = LocalOneHand.current.hand.side,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The line that opens and closes the less-used part of a screen, so a
 * screen can open with a handful of controls and keep the rest one tap away.
 *
 * @param summary what is already set among the hidden things, or what they are
 */
@Composable
fun MoreRow(
    label: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onToggle)
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (!expanded && summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One line of a menu-like sheet: what it does on top, a plain sentence about
 * it underneath. Used where a choice is made between a few named things.
 */
@Composable
fun ChoiceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
