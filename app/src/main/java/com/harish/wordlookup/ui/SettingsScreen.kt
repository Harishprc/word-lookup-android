package com.harish.wordlookup.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.ui.components.PermissionStatusRow
import com.harish.wordlookup.ui.components.SectionCard
import com.harish.wordlookup.ui.theme.Radius
import com.harish.wordlookup.ui.theme.Spacing

/**
 * The permissions/account hub - everything that used to sit loose on Home
 * (the Access card, the language/API-key/App-Info/tile buttons) now lives
 * one tap away from it instead.
 *
 * Plain params, no ViewModel - previewable without a fake, and it makes the
 * "pause for banking" action's real effect ([onPauseForBanking]) explicit at
 * the call site rather than hidden behind a ViewModel method.
 */
@Composable
fun SettingsScreen(
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    showAddTile: Boolean,
    onBack: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onEditSetup: () -> Unit,
    onAddTile: () -> Unit,
    onPauseForBanking: () -> Boolean,
) {
    var pausedJustNow by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ProminentCard),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(Spacing.SM))
            Column {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Permissions & account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Access", modifier = Modifier.padding(top = Spacing.MD)) {
            PermissionStatusRow(
                title = "Display over other apps",
                description = "Needed to show the instant card as an overlay.",
                granted = overlayGranted,
                actionLabel = "Open Settings",
                onClick = onOpenOverlaySettings,
                showDivider = false,
            )
            PermissionStatusRow(
                title = "Accessibility service",
                description = "Needed for the instant (no-tap) trigger. On Android 13+, first open App Info (below) → ⋮ → \"Allow restricted settings\", then enable it here.",
                granted = accessibilityGranted,
                actionLabel = "Open Settings",
                onClick = onOpenAccessibilitySettings,
            )
        }

        SectionCard(title = "Pause for banking") {
            Text(
                "Some banking apps refuse to run while this app's accessibility service is on - they can't tell it apart from a screen-reading trojan. Pausing turns instant lookup off system-wide, not just in this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Justify,
            )
            if (accessibilityGranted) {
                TextButton(
                    onClick = { pausedJustNow = onPauseForBanking() },
                    modifier = Modifier.heightIn(min = 48.dp).padding(top = Spacing.XS),
                ) {
                    Text("Pause now")
                }
                if (pausedJustNow) {
                    Text(
                        "Paused. Android won't let an app turn its own accessibility service back on - to resume, come back here and tap \"Open Settings\" above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.padding(top = Spacing.XS),
                    )
                }
            } else {
                Text(
                    "Already off - accessibility isn't enabled right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(top = Spacing.XS),
                )
            }
        }

        Column(Modifier.padding(top = Spacing.MD)) {
            SettingsActionTile(label = "Change language / API key", onClick = onEditSetup)
            SettingsActionTile(
                label = "Open App Info (for \"Allow restricted settings\")",
                onClick = onOpenAppInfo,
                modifier = Modifier.padding(top = Spacing.SM),
            )

            if (showAddTile) {
                SettingsActionTile(
                    label = "Add tile to Quick Settings",
                    onClick = onAddTile,
                    modifier = Modifier.padding(top = Spacing.SM),
                )
                Text(
                    "If the dialog doesn't appear, add it by hand: open Quick Settings, tap the pencil (edit), then drag \"Word Lookup\" into the panel.",
                    modifier = Modifier.padding(top = Spacing.SM),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Justify,
                )
            }
        }
    }
}

/**
 * The same shadow-tile look `SectionCard` uses (round 7's border→shadow move),
 * for a single-line action row instead of a titled group. Replaces what used
 * to be plain outlined buttons here, which had drifted from the tile system
 * everywhere else on this screen.
 */
@Composable
private fun SettingsActionTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(Radius.XL),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick, role = Role.Button),
    ) {
        Row(
            Modifier.padding(Spacing.MD).heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
