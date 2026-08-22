package com.harish.wordlookup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Monochrome (Rams) role mapping. `primary` is ink, not an accent - the
 * darkest thing on screen is the primary action, so default-styled Button
 * reads correctly with zero call-site changes. The one signal colour
 * (`secondary`) is reserved for the enabled Switch's checked track; nothing
 * else should read it. `secondaryContainer`/`onSecondaryContainer` carry the
 * sunken surface (segmented track, note panels) rather than any accent tint.
 */
private val LightColors = lightColorScheme(
    primary = InkPrimaryLight,
    onPrimary = OnInkLight,
    primaryContainer = SurfaceLight,
    onPrimaryContainer = InkPrimaryLight,
    secondary = SignalLight,
    onSecondary = OnInkLight,
    secondaryContainer = SunkenLight,
    onSecondaryContainer = InkPrimaryLight,
    tertiary = SignalLight,
    onTertiary = OnInkLight,
    background = BackgroundLight,
    onBackground = InkPrimaryLight,
    surface = SurfaceLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = SunkenLight,
    onSurfaceVariant = InkSecondaryLight,
    outline = HairlineLight,
    outlineVariant = HairlineStrongLight,
    error = ErrorLight,
    onError = OnInkLight,
)

private val DarkColors = darkColorScheme(
    primary = InkPrimaryDark,
    onPrimary = OnInkDark,
    primaryContainer = SurfaceDark,
    onPrimaryContainer = InkPrimaryDark,
    secondary = SignalDark,
    onSecondary = OnInkDark,
    secondaryContainer = SunkenDark,
    onSecondaryContainer = InkPrimaryDark,
    tertiary = SignalDark,
    onTertiary = OnInkDark,
    background = BackgroundDark,
    onBackground = InkPrimaryDark,
    surface = SurfaceDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = SunkenDark,
    onSurfaceVariant = InkSecondaryDark,
    outline = HairlineDark,
    outlineVariant = HairlineStrongDark,
    error = ErrorDark,
    onError = OnInkDark,
)

@Composable
fun WordLookupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = WordLookupTypography,
        content = content,
    )
}
