package com.hanifedma.streak.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Streak's theme.
 *
 * Deliberately NOT using Material You dynamic colour: the app's identity is a
 * specific green on near-black, shared with the web app, and letting the
 * device wallpaper repaint it would break that. The theme also follows the
 * app's own setting rather than the system's, because the web app has an
 * in-app toggle and the two should behave identically.
 */

val LocalStreakColors = staticCompositionLocalOf { DarkStreakColors }

private fun darkScheme(c: StreakColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = c.accentContrast,
    secondary = c.accent,
    onSecondary = c.accentContrast,
    background = c.bg,
    onBackground = c.text,
    surface = c.surface,
    onSurface = c.text,
    surfaceVariant = c.surface2,
    onSurfaceVariant = c.muted,
    outline = c.border,
    outlineVariant = c.border,
    error = c.danger,
    onError = c.accentContrast,
    surfaceContainer = c.elevated,
    surfaceContainerHigh = c.surface3,
    surfaceContainerHighest = c.surface3,
    surfaceContainerLow = c.surface2,
    surfaceContainerLowest = c.bg,
    inverseSurface = c.text,
    inverseOnSurface = c.bg,
)

private fun lightScheme(c: StreakColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = c.accentContrast,
    secondary = c.accent,
    onSecondary = c.accentContrast,
    background = c.bg,
    onBackground = c.text,
    surface = c.surface,
    onSurface = c.text,
    surfaceVariant = c.surface2,
    onSurfaceVariant = c.muted,
    outline = c.border,
    outlineVariant = c.border,
    error = c.danger,
    onError = c.accentContrast,
    surfaceContainer = c.elevated,
    surfaceContainerHigh = c.surface3,
    surfaceContainerHighest = c.surface3,
    surfaceContainerLow = c.surface2,
    surfaceContainerLowest = c.bg,
    inverseSurface = c.text,
    inverseOnSurface = c.bg,
)

@Composable
fun StreakTheme(
    darkTheme: Boolean = true, // dark is the app's default, as on the web
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkStreakColors else LightStreakColors
    CompositionLocalProvider(LocalStreakColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(colors) else lightScheme(colors),
            typography = Typography,
            content = content,
        )
    }
}
