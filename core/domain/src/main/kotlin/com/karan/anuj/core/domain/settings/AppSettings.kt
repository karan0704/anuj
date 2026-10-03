package com.karan.anuj.core.domain.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How much larger or smaller than the phone's own setting the app's text is.
 * A fixed set of steps, so it is picked with a tap rather than typed.
 */
enum class TextScale(val factor: Float) {
    SMALL(0.9f),
    NORMAL(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textScale: TextScale = TextScale.NORMAL,
    val appLockEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
)
