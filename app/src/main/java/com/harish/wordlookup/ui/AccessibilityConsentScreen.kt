package com.harish.wordlookup.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.R
import com.harish.wordlookup.ui.theme.Radius
import com.harish.wordlookup.ui.theme.Spacing

/**
 * Interposed between the Settings "Accessibility service" row and the actual
 * system-settings deep link (see MainActivity's ACCESSIBILITY_CONSENT branch)
 * so turning this on is never a single unexplained tap. Reuses the same
 * disclosure string Android's own Accessibility settings page shows, so the
 * two descriptions can't drift apart.
 */
@Composable
fun AccessibilityConsentScreen(
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var understood by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ProminentCard),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.ProminentCard),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNotNow) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(Spacing.XS))
            Column {
                Text("Turn on Accessibility?", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "For the instant, no-tap trigger",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            stringResource(R.string.accessibility_service_description),
            modifier = Modifier.padding(bottom = Spacing.SM),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "This is optional. Word Lookup already works from the text-selection menu without it; this only adds the instant, no-tap trigger.",
            modifier = Modifier.padding(bottom = Spacing.ProminentCard),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(Radius.LG),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.ProminentCard),
        ) {
            Row(Modifier.padding(Spacing.MD), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.SM + Spacing.XS))
                Column {
                    Text("Selections only", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Word Lookup cannot read anything you don't select, and never runs in the background watching your screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = understood,
                onCheckedChange = { understood = it },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary),
            )
            Text("I understand what this permission does.", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = onContinue,
            enabled = understood,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(Radius.LG),
        ) {
            Text("Continue")
        }

        TextButton(
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.XS),
        ) {
            Text("Not now")
        }
    }
}
