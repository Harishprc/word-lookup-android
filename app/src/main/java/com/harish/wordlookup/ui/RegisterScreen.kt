package com.harish.wordlookup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.R
import com.harish.wordlookup.data.RegisterEntry
import com.harish.wordlookup.ui.components.filledFieldColors
import com.harish.wordlookup.ui.theme.Radius
import com.harish.wordlookup.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RegisterScreen(
    entries: List<RegisterEntry>,
    onDelete: (language: String, original: String) -> Unit,
    onBack: () -> Unit,
    dueCount: Int = 0,
    onOpenQuiz: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        entries
    } else {
        entries.filter { matchesQuery(it, query) }
    }

    // Scaffold, not a plain Column ending in the dock: the quiz bar must be
    // a sibling of the scrolling list, not its last item, so it stays fixed
    // at the screen's foot at any scroll position rather than scrolling
    // away with everything else - see CLAUDE.md's "Round 8" section for why
    // this went through two earlier revisions (a header tile, then a
    // Column-with-weight dock) before landing here.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { QuizDock(dueCount = dueCount, onClick = onOpenQuiz) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.ProminentCard)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The only screen in the app with no way back except the system
                // gesture/button - easy to miss, especially with gesture nav where
                // there's no visible back affordance at all.
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(Spacing.SM))
                // Collapsed from a two-line "Your words" / "N saved lookups"
                // to one line - the count restated a fact the list right
                // below it already shows, and the second line cost a row of
                // vertical space this screen doesn't have to spare once the
                // bottom dock (below) also claims some.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Your words", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        " (${entries.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.LG))
                    .padding(vertical = Spacing.MD),
                shape = RoundedCornerShape(Radius.LG),
                colors = filledFieldColors(),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search word, meaning, synonym…") },
                singleLine = true,
            )

            when {
                entries.isEmpty() -> EmptyState(
                    title = "No lookups yet",
                    body = "Select a word in any app and your saved lookups will collect here.",
                )
                filtered.isEmpty() -> EmptyState(
                    title = "No matches",
                    body = "Nothing here matches \"$query\". Try a different word or spelling.",
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    items(filtered) { entry ->
                        RegisterEntryCard(entry, onDelete)
                    }
                }
            }
        }
    }
}

/**
 * The quiz's entry point - full-bleed to the screen edge (not inset by the
 * list's own padding, so it reads as chrome rather than one more card),
 * with an upward shadow so the list visibly reads as continuing underneath
 * it instead of appearing to end. `navigationBarsPadding()` sits it above
 * the gesture-nav pill: this screen is edge-to-edge since round 7's
 * `enableEdgeToEdge()`, so without it the tap target would land on the
 * system gesture area. Present and dimmed rather than hidden when
 * [dueCount] is zero - a control that vanishes teaches nobody it exists.
 */
@Composable
private fun QuizDock(dueCount: Int, onClick: () -> Unit) {
    val active = dueCount > 0
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(enabled = active, onClick = onClick, role = Role.Button)
                .padding(horizontal = Spacing.ProminentCard, vertical = Spacing.MD)
                .alpha(if (active) 1f else 0.5f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Quiz", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (active) "$dueCount ${if (dueCount == 1) "word" else "words"} due today" else "Nothing due right now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EmptyState(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Spacing.ProminentCard, vertical = Spacing.LG),
        ) {
            Box(
                Modifier
                    .padding(bottom = Spacing.MD)
                    .size(width = 32.dp, height = 3.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            )
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.XS),
            )
        }
    }
}

/**
 * A saved lookup, rendered as the exact popup card ([LookupResultBody]) plus
 * a quiet delete affordance and a language/date footer - both drawn in the
 * card's own `pos_text` grey so they read as part of its ladder, not as an
 * import of app-theme colour into it.
 *
 * Wrapped in `MaterialTheme(typography = Typography())` so [LookupResultBody]
 * resolves against the same stock M3 scale it gets from `OverlayHost` (which
 * applies no theme at all) - without this the register's copy of the card
 * would render at the app's larger `WordLookupTypography` sizes instead and
 * the two would visibly disagree despite sharing this composable.
 *
 * [entry.language] (not the app's current target language) drives the
 * speaker (round 8): a word saved under a language the user has since
 * switched away from must still speak in the language it was saved in.
 */
@Composable
internal fun RegisterEntryCard(entry: RegisterEntry, onDelete: (String, String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val speech = rememberCardSpeech(entry.result, languageName = entry.language)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(colorResource(R.color.card_top), colorResource(R.color.card_bottom)),
                ),
            )
            .padding(16.dp),
    ) {
        MaterialTheme(typography = Typography()) {
            Column {
                LookupResultBody(entry.result, emphasizeWordAndTranslation = true, speech = speech)
                Text(
                    "${entry.language} · ${formatDate(entry.createdAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorResource(R.color.pos_text),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        IconButton(
            onClick = { onDelete(entry.language, entry.result.original) },
            modifier = Modifier.align(Alignment.TopEnd).size(40.dp),
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete ${entry.result.original}",
                tint = colorResource(R.color.pos_text),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun matchesQuery(entry: RegisterEntry, query: String): Boolean {
    val q = query.trim()
    val r = entry.result
    return listOf(r.original, r.meaning, r.synonyms, r.translation, r.synonymsNative).any {
        it.contains(q, ignoreCase = true)
    }
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMillis))
