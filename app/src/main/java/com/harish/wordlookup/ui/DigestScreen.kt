package com.harish.wordlookup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.data.DigestWeek
import com.harish.wordlookup.data.RegisterEntry
import com.harish.wordlookup.ui.theme.Spacing

/**
 * The week's saved lookups rendered as [RegisterEntryCard] - the same object
 * the register itself uses, not a second layout (round 6's rule). No
 * counts-that-score-you, no streak: the headline is a plain count.
 */
@Composable
fun DigestScreen(
    entries: List<RegisterEntry>,
    weekStartMillis: Long,
    onDelete: (language: String, original: String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.ProminentCard)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(Spacing.SM))
                Text("This week", style = MaterialTheme.typography.headlineMedium)
            }

            val word = if (entries.size == 1) "word" else "words"
            Text(
                "${entries.size} new $word",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = Spacing.SM),
            )
            Text(
                DigestWeek.rangeLabel(weekStartMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.XS, bottom = Spacing.ProminentCard),
            )

            if (entries.isEmpty()) {
                EmptyState(
                    title = "Nothing new this week",
                    body = "Words you save this week will show up here.",
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    items(entries) { entry ->
                        RegisterEntryCard(entry, onDelete)
                    }
                }
            }
        }
    }
}
