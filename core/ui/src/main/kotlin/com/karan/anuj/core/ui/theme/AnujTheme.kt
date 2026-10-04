package com.karan.anuj.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * A calm palette: one teal accent for the things that can be tapped, and
 * neutral surfaces everywhere else, so the accent always means "act here".
 */
private val TealLight = lightColorScheme(
    primary = Color(0xFF00696B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F2),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF051F20),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE4E4),
    onSurfaceVariant = Color(0xFF3F4949),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val TealDark = darkColorScheme(
    primary = Color(0xFF80D4D6),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51),
    onPrimaryContainer = Color(0xFF9CF1F2),
    secondary = Color(0xFFB0CCCC),
    onSecondary = Color(0xFF1B3435),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E8),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/**
 * Warm paper instead of white, ink instead of black, and one deep indigo
 * accent. Pure white behind pure black is the harshest pair for long
 * reading; both ends are pulled in here, while body text still clears the
 * 7:1 contrast mark. Every surface level is set, so cards, sheets and the
 * bottom bar stay in the same warm family instead of falling back to the
 * library's default tints.
 */
private val IvoryLight = lightColorScheme(
    primary = Color(0xFF3F4C7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1F5),
    onPrimaryContainer = Color(0xFF131B3A),
    inversePrimary = Color(0xFFB9C3F0),
    secondary = Color(0xFF6B5E4B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE4D3),
    onSecondaryContainer = Color(0xFF241B0E),
    tertiary = Color(0xFF7A5C22),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E3BC),
    onTertiaryContainer = Color(0xFF2A1D00),
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF1F1C18),
    surface = Color(0xFFFAF7F2),
    onSurface = Color(0xFF1F1C18),
    surfaceVariant = Color(0xFFEAE4DA),
    onSurfaceVariant = Color(0xFF4D473F),
    surfaceTint = Color(0xFF3F4C7A),
    inverseSurface = Color(0xFF34302B),
    inverseOnSurface = Color(0xFFF7F0E7),
    error = Color(0xFFA63A2B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFADAD4),
    onErrorContainer = Color(0xFF3B0A04),
    outline = Color(0xFF7F786E),
    outlineVariant = Color(0xFFD5CEC3),
    surfaceBright = Color(0xFFFAF7F2),
    surfaceDim = Color(0xFFDDD8CF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F1EA),
    surfaceContainer = Color(0xFFF0EBE3),
    surfaceContainerHigh = Color(0xFFEAE5DC),
    surfaceContainerHighest = Color(0xFFE4DED5),
)

private val IvoryDark = darkColorScheme(
    primary = Color(0xFFB9C3F0),
    onPrimary = Color(0xFF1F2A52),
    primaryContainer = Color(0xFF36426E),
    onPrimaryContainer = Color(0xFFDDE1F5),
    inversePrimary = Color(0xFF3F4C7A),
    secondary = Color(0xFFD6C7B0),
    onSecondary = Color(0xFF3A2F1F),
    secondaryContainer = Color(0xFF524634),
    onSecondaryContainer = Color(0xFFEFE4D3),
    tertiary = Color(0xFFE3C685),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5C4310),
    onTertiaryContainer = Color(0xFFF6E3BC),
    background = Color(0xFF161412),
    onBackground = Color(0xFFEAE3DA),
    surface = Color(0xFF161412),
    onSurface = Color(0xFFEAE3DA),
    surfaceVariant = Color(0xFF4D473F),
    onSurfaceVariant = Color(0xFFD0C8BC),
    surfaceTint = Color(0xFFB9C3F0),
    inverseSurface = Color(0xFFEAE3DA),
    inverseOnSurface = Color(0xFF34302B),
    error = Color(0xFFFFB4A6),
    onError = Color(0xFF5F150A),
    errorContainer = Color(0xFF7F2A1C),
    onErrorContainer = Color(0xFFFADAD4),
    outline = Color(0xFF999084),
    outlineVariant = Color(0xFF4D473F),
    surfaceBright = Color(0xFF3D3935),
    surfaceDim = Color(0xFF161412),
    surfaceContainerLowest = Color(0xFF100E0C),
    surfaceContainerLow = Color(0xFF1E1B18),
    surfaceContainer = Color(0xFF22201C),
    surfaceContainerHigh = Color(0xFF2D2A26),
    surfaceContainerHighest = Color(0xFF383430),
)

/** A named set of colours with a light and a dark form. */
enum class AnujPalette(internal val light: ColorScheme, internal val dark: ColorScheme) {
    TEAL(TealLight, TealDark),
    IVORY(IvoryLight, IvoryDark),
    ;

    companion object {
        /**
         * The saved choice arrives by name, because this module does not know
         * the settings. A name it does not recognise, or none yet, is [TEAL].
         */
        fun named(name: String?): AnujPalette = entries.firstOrNull { it.name == name } ?: TEAL
    }
}

/**
 * @param textScaleFactor multiplied onto the phone's own font size setting, so
 * the app's text-size choice stacks with the system one instead of replacing it.
 */
@Composable
fun AnujTheme(
    darkTheme: Boolean,
    palette: AnujPalette = AnujPalette.TEAL,
    textScaleFactor: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scaledDensity = Density(
        density = density.density,
        fontScale = density.fontScale * textScaleFactor,
    )
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = if (darkTheme) palette.dark else palette.light,
            content = content,
        )
    }
}
