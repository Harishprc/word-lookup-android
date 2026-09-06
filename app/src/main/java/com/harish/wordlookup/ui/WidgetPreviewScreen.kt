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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.ui.theme.Spacing

/**
 * Round 10: a static, purely illustrative preview - not a real
 * `AppWidgetProvider`, no home-screen integration. All state here is local
 * `remember`, never touching `Settings`/`LookupTileService` - the header
 * says so plainly so this never reads as the real thing.
 */
@Composable
fun WidgetPreviewScreen(languageGlyph: String, onBack: () -> Unit) {
    var wotdIndex by remember { mutableStateOf(0) }
    var digestFlip by remember { mutableStateOf(false) }
    var shadeOpen by remember { mutableStateOf(false) }
    var tileOn by remember { mutableStateOf(true) }

    val word = DEMO_WORDS[wotdIndex % DEMO_WORDS.size]

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF4A4F58), Color(0xFF23262B), Color(0xFF16181C)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.ProminentCard),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White.copy(alpha = 0.9f))
                }
                Spacer(Modifier.width(Spacing.SM))
                Text(
                    "Home screen, widgets in place",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.66f),
                )
            }
            Text(
                "A preview. The real widgets are already available from your launcher's own Add widget flow, and the tile from Add tile above.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = Spacing.XS, bottom = Spacing.MD),
            )

            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(150.dp).clickable { wotdIndex++ },
                ) {
                    Column(Modifier.padding(16.dp).fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            GlyphMark(languageGlyph, size = 26.dp)
                            Text("2×2", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9298A1))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(word.word, style = MaterialTheme.typography.titleMedium, color = Color(0xFF1A1A1A))
                        Text(word.translation, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF111111))
                    }
                }
                Column(Modifier.weight(1f).padding(start = Spacing.MD, top = 4.dp)) {
                    Text(
                        "WORD OF THE DAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Text(
                        "Drawn from your own register, tap to cycle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                        modifier = Modifier.padding(top = Spacing.XS),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.MD)
                    .clickable { digestFlip = !digestFlip },
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphMark(languageGlyph, size = 24.dp)
                            Spacer(Modifier.width(Spacing.SM))
                            Text(
                                if (digestFlip) "THIS WEEK · 12 NEW" else "WORD OF THE DAY",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8A8F9C),
                            )
                        }
                        Text("4×2", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9298A1))
                    }
                    if (digestFlip) {
                        Text(
                            "Tap to see this week's words.",
                            modifier = Modifier.padding(top = Spacing.SM),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF1A1A1A),
                        )
                    } else {
                        Text(
                            word.word,
                            modifier = Modifier.padding(top = Spacing.SM),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF1A1A1A),
                        )
                        Text(word.translation, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF111111))
                    }
                }
            }

            Text(
                "Tap the wide tile to preview the weekly-digest flip.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.42f),
                modifier = Modifier.padding(top = Spacing.SM),
            )

            Row(Modifier.padding(top = Spacing.LG), verticalAlignment = Alignment.CenterVertically) {
                GlyphMark(languageGlyph, size = 52.dp, cornerRadius = 15.dp)
                Column(Modifier.padding(start = Spacing.MD)) {
                    Text("Word Lookup", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    Text(
                        "Launcher icon: same ink ground, current language's glyph.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                modifier = Modifier.padding(top = Spacing.LG).clickable { shadeOpen = true },
            ) {
                Text("Pull shade", modifier = Modifier.padding(horizontal = Spacing.MD, vertical = Spacing.SM))
            }
        }

        if (shadeOpen) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { shadeOpen = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
                    color = Color(0xFF16181C),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Quick Settings", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                        }
                        Row(Modifier.padding(top = Spacing.MD)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (tileOn) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.09f),
                                modifier = Modifier.weight(1f).clickable { tileOn = !tileOn },
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    GlyphMark(languageGlyph, size = 30.dp, cornerRadius = 9.dp)
                                    Column(Modifier.padding(start = Spacing.SM)) {
                                        Text(
                                            "Word Lookup",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (tileOn) Color.White else Color.White.copy(alpha = 0.8f),
                                        )
                                        Text(
                                            if (tileOn) "Instant on" else "Instant off",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (tileOn) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.45f),
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "The tile switches the instant popup on and off, the same setting as Home's \"Instant\"/\"Menu\" segments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.42f),
                            modifier = Modifier.padding(top = Spacing.MD),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlyphMark(glyph: String, size: androidx.compose.ui.unit.Dp, cornerRadius: androidx.compose.ui.unit.Dp = 8.dp) {
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = Color(0xFF16181C),
        contentColor = Color.White,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private data class DemoWord(val word: String, val translation: String)

private val DEMO_WORDS = listOf(
    DemoWord("salient", "ಪ್ರಮುಖ"),
    DemoWord("corollary", "ಉಪಸಿದ್ಧಾಂತ"),
    DemoWord("mitigate", "ತಗ್ಗಿಸು"),
)
