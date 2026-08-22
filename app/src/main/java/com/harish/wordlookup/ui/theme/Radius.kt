package com.harish.wordlookup.ui.theme

import androidx.compose.ui.unit.dp

/**
 * DESIGN.md's `rounded:` scale, applied explicitly per-composable (matching how corners are
 * already hardcoded today) rather than as a global `MaterialTheme` shape override, which would
 * silently reshape every default-styled component app-wide.
 */
object Radius {
    val SM = 4.dp
    val MD = 8.dp
    val LG = 12.dp
    val XL = 16.dp
}
