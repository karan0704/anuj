package com.karan.anuj.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The frame every full screen sits in.
 *
 * It draws behind the status and navigation bars and keeps content clear of
 * them, and it lifts the content above the keyboard when the keyboard opens,
 * so a focused field and the button under it are never covered. The bottom
 * bar does not ride up with the keyboard; it stays underneath it, the way a
 * tab bar behaves in a chat app.
 *
 * The inner padding is marked as consumed before [imePadding] is applied, so
 * the keyboard only adds the space the bottom bar has not already taken.
 */
@Composable
fun AnujScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding(),
            content = content,
        )
    }
}
