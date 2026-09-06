package com.harish.wordlookup.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A flat `surface` fill, no focus/unfocus underline (round 10 retired the
 * recessed `surfaceVariant` fill this used to read). `TextFieldColors` has no
 * border slot for the filled decoration box, so callers add their own
 * hairline via `Modifier.border(1.dp, colorScheme.outline, shape)`.
 */
@Composable
fun filledFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)
