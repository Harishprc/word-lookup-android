package com.harish.wordlookup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.ui.theme.Spacing

/**
 * "1 of 2" step indicator as two short bars - a filled bar per completed
 * step, a hairline-strong bar for what's left - plus a tracked numeral.
 * No pill, no colour: progress reads as a count, not a decoration.
 */
@Composable
fun OnboardingProgress(step: Int, totalSteps: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Step $step of $totalSteps" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            Box(
                index < step,
                modifier = Modifier.padding(end = Spacing.XS),
            )
        }
        Text(
            "$step/$totalSteps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.XS),
        )
    }
}

@Composable
private fun Box(filled: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(width = 18.dp, height = 3.dp)
            .background(
                if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(2.dp),
            ),
    )
}
