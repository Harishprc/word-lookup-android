package com.harish.wordlookup.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.data.Language
import com.harish.wordlookup.data.Languages
import com.harish.wordlookup.ui.theme.Spacing

/**
 * Round 10: a full-screen glyph grid, reached only from Settings' "Target
 * language" row - the language is a picture (the launcher icon, the tile,
 * the Home mark) as much as a name, so the picker shows pictures. The
 * dropdown inside SetupScreen's onboarding/edit flow is left untouched (see
 * CLAUDE.md's Round 10 section for why); this is a second, additive entry
 * point, not a replacement.
 *
 * [Languages.ALL] has no "Indian"/"World" field of its own - the split below
 * is a fixed index range (0..10 Indian, 11..24 World) matching the list's
 * existing, stable order, kept in the UI layer so nothing that iterates
 * `Languages.ALL` elsewhere in the app needs to change.
 */
@Composable
fun LanguagePickerScreen(
    current: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val indian = Languages.ALL.subList(0, 11)
    val world = Languages.ALL.subList(11, Languages.ALL.size)

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
            Text("Target language", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            "The glyph you pick becomes the launcher icon, the tile, and the mark on Home.",
            modifier = Modifier.padding(top = Spacing.SM, bottom = Spacing.MD),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Justify,
        )

        LanguageGroup(title = "Indian", languages = indian, current = current, onPick = onPick)
        LanguageGroup(
            title = "World",
            languages = world,
            current = current,
            onPick = onPick,
            modifier = Modifier.padding(top = Spacing.MD),
        )
    }
}

/**
 * A plain `Column` of `Row`s, not `LazyVerticalGrid` - at most 14 items per
 * group, and the whole screen already scrolls as one `Column`; a lazy grid
 * inside a scrolling column needs its own bounded height, which a fixed
 * 4-per-row chunk avoids needing at all.
 */
@Composable
private fun LanguageGroup(
    title: String,
    languages: List<Language>,
    current: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.SM),
        )
        languages.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = Spacing.MD),
                horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
            ) {
                row.forEach { lang ->
                    Box(Modifier.weight(1f)) {
                        LanguageTile(lang = lang, selected = lang.name == current, onClick = { onPick(lang.name) })
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LanguageTile(lang: Language, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick, role = Role.Button),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            shadowElevation = if (selected) 8.dp else 2.dp,
            modifier = Modifier.size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(lang.glyph, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text(
            lang.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.XS),
        )
    }
}
