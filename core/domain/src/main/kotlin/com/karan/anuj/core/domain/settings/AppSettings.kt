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
    /** How many days a task stays in the trash before it is removed for good; null keeps it until the user empties the trash. */
    val emptyTrashAfterDays: Int? = null,
    /** How many days the photos of a task removed for good are held back before their files are deleted; 0 deletes them with the task. */
    val keepRemovedPhotosDays: Int = 0,
    /** How many days the logs (what was edited, which reminders were shown) are kept; null keeps them always. */
    val keepHistoryDays: Int? = null,
    /** How long the app may be out of view before it asks to be unlocked again, in seconds. */
    val lockAfterSeconds: Int = DEFAULT_LOCK_AFTER_SECONDS,
) {
    companion object {
        const val DEFAULT_LOCK_AFTER_SECONDS = 60

        /** A week, one, two, three and six months, a year: the waits offered before the trash is emptied by itself. */
        val TRASH_DAYS_CHOICES: List<Int> = listOf(7, 30, 60, 90, 180, 365)

        /** At once, a week, a month, three months. */
        val PHOTO_DAYS_CHOICES: List<Int> = listOf(0, 7, 30, 90)

        /** One, three and six months, a year. Never shorter than a month, because the daily reminder limit reads today's log. */
        val HISTORY_DAYS_CHOICES: List<Int> = listOf(30, 90, 180, 365)

        /** The delays offered as chips: straight away, half a minute, one, five and fifteen minutes. */
        val LOCK_AFTER_CHOICES: List<Int> = listOf(0, 30, 60, 300, 900)
    }
}
