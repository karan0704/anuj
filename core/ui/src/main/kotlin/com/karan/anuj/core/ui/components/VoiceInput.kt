package com.karan.anuj.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The microphone button for a text field: it listens for one sentence and
 * hands back the words.
 *
 * The button itself belongs to the voice feature. A text field in any other
 * feature shows it through this, so no feature depends on voice; the app
 * shell supplies the real button once, at the top. Where nothing supplies
 * one (a preview, a test of a single screen) the field simply has no
 * microphone.
 *
 * Use it as a field's trailing icon:
 *
 *     trailingIcon = { LocalVoiceInput.current { spoken -> name = spoken } }
 */
val LocalVoiceInput = staticCompositionLocalOf<@Composable (onText: (String) -> Unit) -> Unit> { {} }
