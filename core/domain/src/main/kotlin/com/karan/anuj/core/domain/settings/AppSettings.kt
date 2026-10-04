package com.karan.anuj.core.domain.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The set of colours the app is drawn in. Each has a light and a dark form,
 * so this choice and [ThemeMode] are independent.
 */
enum class ColourStyle { TEAL, IVORY }

/** Which thumb the screens are laid out for: it decides the side the add button and a list's actions sit on. */
enum class HandSide { RIGHT, LEFT }

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
    val colourStyle: ColourStyle = ColourStyle.TEAL,
    val textScale: TextScale = TextScale.NORMAL,
    val handSide: HandSide = HandSide.RIGHT,
    /** A list starts part of the way down the screen, within the thumb's reach, and rises as it is scrolled. */
    val lowerLists: Boolean = true,
    val appLockEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    /** How long the app may be out of view before it asks to be unlocked again, in seconds. */
    val lockAfterSeconds: Int = DEFAULT_LOCK_AFTER_SECONDS,
) {
    companion object {
        const val DEFAULT_LOCK_AFTER_SECONDS = 60

        /** The delays offered as chips: straight away, half a minute, one, five and fifteen minutes. */
        val LOCK_AFTER_CHOICES: List<Int> = listOf(0, 30, 60, 300, 900)
    }
}
