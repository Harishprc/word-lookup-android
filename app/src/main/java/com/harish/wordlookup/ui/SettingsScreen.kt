package com.harish.wordlookup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.harish.wordlookup.data.RegisterEntry
import com.harish.wordlookup.data.Settings
import com.harish.wordlookup.data.export.RegisterCsvExporter
import com.harish.wordlookup.ui.components.IconSegmentedControl
import com.harish.wordlookup.ui.components.PermissionStatusRow
import com.harish.wordlookup.ui.components.SectionCard
import com.harish.wordlookup.ui.theme.Radius
import com.harish.wordlookup.ui.theme.Spacing
import kotlinx.coroutines.launch

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
    reminderEnabled: Boolean = false,
    reminderHour: Int = Settings.DEFAULT_REMINDER_HOUR,
    onReminderEnabledChange: (Boolean) -> Unit = {},
    onReminderHourChange: (Int) -> Unit = {},
    languageName: String = "Kannada",
    languageGlyph: String = "ಕ",
    onOpenLanguage: () -> Unit = {},
    registerEntries: List<RegisterEntry> = emptyList(),
    onOpenWidgetPreview: () -> Unit = {},
) {
    var pausedJustNow by remember { mutableStateOf(false) }
    // accessibilityGranted can flip back to true independently of this
    // screen (system Settings, or re-enabling from Access above) once
    // round 9's ContentObserver fix makes it live rather than resume-only -
    // once it's genuinely back on, "Paused..." is no longer an accurate
    // thing to keep showing.
    LaunchedEffect(accessibilityGranted) {
        if (accessibilityGranted) pausedJustNow = false
    }

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
            // Round 9: the message used to live *inside* the accessibilityGranted
            // branch, which was fine only because accessibilityGranted never
            // actually changed after a pause (a stale read - see MainActivity's
            // ContentObserver fix). Now that it does, keeping the confirmation
            // inside that branch would swap it for "Already off" the instant the
            // tap that triggered it took effect. Hoisted out so pausedJustNow
            // alone decides whether the confirmation shows.
            if (accessibilityGranted) {
                PausePill(onClick = { pausedJustNow = onPauseForBanking() }, modifier = Modifier.padding(top = Spacing.XS))
            } else if (!pausedJustNow) {
                Text(
                    "Already off - accessibility isn't enabled right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(top = Spacing.XS),
                )
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
        }

        TargetLanguageRow(
            languageName = languageName,
            languageGlyph = languageGlyph,
            onClick = onOpenLanguage,
            modifier = Modifier.padding(top = Spacing.MD),
        )

        // Round 10: "Change language / API key" split into the dedicated
        // Target language row above plus this tile, relabeled since language
        // now has its own destination - this one only edits the key (and,
        // via SetupScreen's own dropdown, still doubles as a full replay of
        // both fields at once).
        SettingsActionTile(
            label = "Gemini API key",
            onClick = onEditSetup,
            modifier = Modifier.padding(top = Spacing.MD),
        )

        RegisterExportCard(entries = registerEntries, modifier = Modifier.padding(top = Spacing.MD))

        SectionCard(title = "Review reminder", modifier = Modifier.padding(top = Spacing.MD)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Remind me to review",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = onReminderEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.secondary,
                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                )
            }
            // Nothing to configure until the switch is on, so the hour
            // picker only appears then rather than sitting there inert.
            // Icons, not words (round 9) - "Afternoon" wrapped to two lines
            // as text at this width. contentDescriptions carries the word
            // into the accessibility tree even though it's off the screen.
            if (reminderEnabled) {
                val hours = REMINDER_HOUR_OPTIONS
                IconSegmentedControl(
                    icons = hours.map { REMINDER_HOUR_ICONS.getValue(it) },
                    contentDescriptions = hours.map { formatHourLabel(it) },
                    selectedIndex = hours.indexOf(reminderHour).coerceAtLeast(0),
                    onSelect = { onReminderHourChange(hours[it]) },
                    modifier = Modifier.padding(top = Spacing.SM),
                )
            }
            Text(
                "A silent reminder when saved words are due for review. Never more than one a day, and never when nothing is due.",
                modifier = Modifier.padding(top = Spacing.SM),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Justify,
            )
        }

        Column(Modifier.padding(top = Spacing.MD)) {
            SettingsActionTile(label = "See widgets before adding", onClick = onOpenWidgetPreview)

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

            SettingsActionTile(
                label = "Open App Info",
                subtitle = "For \"Allow restricted settings\"",
                onClick = onOpenAppInfo,
                modifier = Modifier.padding(top = Spacing.SM),
            )
        }
    }
}

/** Morning/Afternoon/Evening/Night labels - kept as the one definition of what each hour is called, now living in the accessibility tree via IconSegmentedControl's contentDescriptions rather than on screen. */
private val REMINDER_HOUR_OPTIONS = Settings.DEFAULT_REMINDER_HOURS
private fun formatHourLabel(hour: Int): String = when (hour) {
    9 -> "Morning"
    14 -> "Afternoon"
    19 -> "Evening"
    21 -> "Night"
    else -> "$hour:00"
}

/**
 * Round 9: icons instead of words - "Afternoon" wrapped to two lines as
 * text at the four-segment width. Two suns, two moons, each pair visually
 * distinct: WbSunny is deliberately not used alongside LightMode, since the
 * two are near-identical suns and would make Morning/Afternoon
 * indistinguishable at a glance.
 */
private val REMINDER_HOUR_ICONS: Map<Int, ImageVector> = mapOf(
    9 to Icons.Filled.WbTwilight, // sunrise
    14 to Icons.Filled.LightMode, // full sun, highest-contrast of the four
    19 to Icons.Filled.NightsStay, // moon behind cloud: dusk
    21 to Icons.Filled.Bedtime, // moon: late
)

/**
 * Round 9: replaces a bare TextButton, which read as the weakest control on
 * a screen where every other action is a shadow-tile or a filled button,
 * despite being the one action this whole card exists for. Ink fill, not
 * the signal accent - DESIGN.md reserves that strictly for on/active state
 * (the enabled switch, a granted permission's dot), and pausing is neither.
 * Same containerColor/contentColor pairing ReviewScreen's "Show answer"
 * already uses for its primary action - reuse, not a new treatment.
 */
@Composable
private fun PausePill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.LG),
        color = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.MD, vertical = Spacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pause now", style = MaterialTheme.typography.bodyLarge)
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
private fun SettingsActionTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
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
            if (subtitle == null) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            } else {
                Column {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Round 10: its own destination, not folded into the language+key edit
 * screen - a leading glyph avatar (the same mark the launcher icon and tile
 * already show) makes clear this row picks the icon, not just a name.
 */
@Composable
private fun TargetLanguageRow(languageName: String, languageGlyph: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            Modifier.padding(Spacing.MD),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(languageGlyph, color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(Spacing.SM + Spacing.XS))
            Column(Modifier.weight(1f)) {
                Text("Target language", style = MaterialTheme.typography.bodyLarge)
                Text(
                    languageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Round 10: CSV only this round - API 29+ (MediaStore.Downloads, zero
 * permissions), no Anki button (omitted rather than shown disabled) and no
 * Share action (would need a new FileProvider manifest entry) - both
 * deferred, see CLAUDE.md's Round 10 section. The confirmation shows a
 * constructed display string, not a real filesystem path: scoped storage
 * doesn't hand one back.
 */
@Composable
private fun RegisterExportCard(entries: List<RegisterEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<RegisterCsvExporter.Result?>(null) }

    SectionCard(title = "Export register", modifier = modifier) {
        val word = if (entries.size == 1) "word" else "words"
        Text(
            "${entries.size} $word, with meanings and translations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { scope.launch { result = RegisterCsvExporter.export(context, entries) } },
            modifier = Modifier.padding(top = Spacing.SM),
            shape = RoundedCornerShape(Radius.LG),
        ) {
            Text("Save CSV")
        }

        when (val r = result) {
            is RegisterCsvExporter.Result.Success -> Row(
                modifier = Modifier.padding(top = Spacing.SM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                )
                Spacer(Modifier.width(Spacing.SM))
                Column {
                    Text("CSV saved to Downloads.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Downloads/${r.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is RegisterCsvExporter.Result.Failure -> Text(
                r.reason,
                modifier = Modifier.padding(top = Spacing.SM),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            null -> Unit
        }
    }
}
