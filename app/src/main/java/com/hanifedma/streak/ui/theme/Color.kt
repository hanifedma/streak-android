package com.hanifedma.streak.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The web app's palette, verbatim, so Streak looks like itself wherever it
 * runs. Material 3's own colour roles don't cover everything this app needs
 * (three surface tiers, cell states, ten habit colours), so those live in
 * [StreakColors] and reach the UI through a CompositionLocal.
 */

// ---------- Dark (default) — matched to hanifedma.com ----------
val DarkBg = Color(0xFF0F0F0F)
val DarkSurface = Color(0xFF1A1A1A)
val DarkSurface2 = Color(0xFF202021)
val DarkSurface3 = Color(0xFF282829)
val DarkElevated = Color(0xFF232323)
val DarkBorder = Color(0xFF333333)
val DarkBorderStrong = Color(0xFF454545)
val DarkText = Color(0xFFF0F0F0)
val DarkMuted = Color(0xFFA6A6A6)
val DarkFaint = Color(0xFF6F6F6F)
val DarkAccent = Color(0xFF22C55E)
val DarkAccentHover = Color(0xFF4ADE80)
val DarkAccentContrast = Color(0xFF052E16)
val DarkDanger = Color(0xFFF4566B)

// Amber, for the one action that is irreversible but destroys nothing you
// chose to keep. Red stays reserved for deletion, so the two never blur.
val DarkWarn = Color(0xFFFBBF24)
val DarkCellEmpty = Color(0xFF2A2A2B)
val DarkCellOff = Color(0xFF1F1F20)
val DarkTrack = Color(0xFF2A2A2B)

// ---------- Light ----------
val LightBg = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFF3F4F6)
val LightSurface3 = Color(0xFFE8EAED)
val LightElevated = Color(0xFFFFFFFF)
val LightBorder = Color(0xFFE5E7EB)
val LightBorderStrong = Color(0xFFD1D5DB)
val LightText = Color(0xFF1A1A1A)
val LightMuted = Color(0xFF565B64)
val LightFaint = Color(0xFF868E96)
val LightAccent = Color(0xFF16A34A)
val LightAccentHover = Color(0xFF15803D)
val LightAccentContrast = Color(0xFFFFFFFF)
val LightDanger = Color(0xFFE11D48)

// Darker than the dark theme's amber: #FBBF24 on white fails contrast.
val LightWarn = Color(0xFFB45309)
val LightCellEmpty = Color(0xFFE9EBEE)
val LightCellOff = Color(0xFFF5F6F8)
val LightTrack = Color(0xFFE9EBEE)

/**
 * Habit colours: pastel on black, deeper on white, so a habit stays legible in
 * either theme without changing identity.
 */
private val HabitDark = mapOf(
    "green" to Color(0xFF4ADE80),
    "teal" to Color(0xFF2DD4BF),
    "sky" to Color(0xFF38BDF8),
    "blue" to Color(0xFF60A5FA),
    "indigo" to Color(0xFFA5B4FC),
    "purple" to Color(0xFFC084FC),
    "pink" to Color(0xFFF9A8D4),
    "red" to Color(0xFFFB7185),
    "amber" to Color(0xFFFBBF24),
    "lime" to Color(0xFFA3E635),
)

private val HabitLight = mapOf(
    "green" to Color(0xFF16A34A),
    "teal" to Color(0xFF0D9488),
    "sky" to Color(0xFF0284C7),
    "blue" to Color(0xFF2563EB),
    "indigo" to Color(0xFF4F46E5),
    "purple" to Color(0xFF9333EA),
    "pink" to Color(0xFFDB2777),
    "red" to Color(0xFFE11D48),
    "amber" to Color(0xFFD97706),
    "lime" to Color(0xFF65A30D),
)

/** The colours this app draws with, beyond Material's own roles. */
data class StreakColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val elevated: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentHover: Color,
    val accentContrast: Color,
    val danger: Color,
    val warn: Color,
    val cellEmpty: Color,
    val cellOff: Color,
    val track: Color,
) {
    /** A habit's colour in the active theme; falls back rather than crashing. */
    fun habit(name: String): Color {
        val table = if (dark) HabitDark else HabitLight
        return table[name] ?: table.getValue("green")
    }
}

val DarkStreakColors = StreakColors(
    dark = true,
    bg = DarkBg, surface = DarkSurface, surface2 = DarkSurface2, surface3 = DarkSurface3,
    elevated = DarkElevated, border = DarkBorder, borderStrong = DarkBorderStrong,
    text = DarkText, muted = DarkMuted, faint = DarkFaint,
    accent = DarkAccent, accentHover = DarkAccentHover, accentContrast = DarkAccentContrast,
    danger = DarkDanger, warn = DarkWarn,
    cellEmpty = DarkCellEmpty, cellOff = DarkCellOff, track = DarkTrack,
)

val LightStreakColors = StreakColors(
    dark = false,
    bg = LightBg, surface = LightSurface, surface2 = LightSurface2, surface3 = LightSurface3,
    elevated = LightElevated, border = LightBorder, borderStrong = LightBorderStrong,
    text = LightText, muted = LightMuted, faint = LightFaint,
    accent = LightAccent, accentHover = LightAccentHover, accentContrast = LightAccentContrast,
    danger = LightDanger, warn = LightWarn,
    cellEmpty = LightCellEmpty, cellOff = LightCellOff, track = LightTrack,
)

/** Reachable anywhere as `Streak.colors`. */
object Streak {
    val colors: StreakColors
        @Composable @ReadOnlyComposable get() = LocalStreakColors.current
}
