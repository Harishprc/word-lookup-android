package com.harish.wordlookup.ui.previews

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.harish.wordlookup.data.LookupResult
import com.harish.wordlookup.data.RegisterEntry
import com.harish.wordlookup.data.TriggerMode
import com.harish.wordlookup.ui.CardState
import com.harish.wordlookup.ui.HomeHeader
import com.harish.wordlookup.ui.LookupCard
import com.harish.wordlookup.ui.RegisterScreen
import com.harish.wordlookup.ui.SettingsScreen
import com.harish.wordlookup.ui.SetupScreen
import com.harish.wordlookup.ui.SummaryCard
import com.harish.wordlookup.ui.components.NavigationCard
import com.harish.wordlookup.ui.components.SectionCard
import com.harish.wordlookup.ui.components.SegmentedControl
import com.harish.wordlookup.ui.theme.Spacing
import com.harish.wordlookup.ui.theme.WordLookupTheme

/*
 * Design-time previews only. Nothing here is referenced by the running app -
 * @Preview functions are never invoked outside the tooling renderer.
 */

private val SAMPLE_RESULT = LookupResult(
    original = "ephemeral",
    translation = "ಕ್ಷಣಿಕ",
    partOfSpeech = "adjective",
    meaning = "Lasting for a very short time.",
    synonyms = "fleeting, transient, momentary",
    exampleEn = "Fame in this industry is ephemeral.",
    exampleNative = "ಈ ಉದ್ಯಮದಲ್ಲಿ ಖ್ಯಾತಿ ಕ್ಷಣಿಕ.",
    synonymsNative = "ನಶ್ವರ, ಕ್ಷಣಭಂಗುರ, ತಾತ್ಕಾಲಿಕ",
)

private val SAMPLE_ENTRIES = listOf(
    RegisterEntry(SAMPLE_RESULT, "Kannada", 1_724_198_400_000L),
    RegisterEntry(
        LookupResult(
            original = "resilient",
            translation = "ಚೇತರಿಸಿಕೊಳ್ಳುವ",
            partOfSpeech = "adjective",
            meaning = "Able to recover quickly from difficulty.",
            synonyms = "strong, adaptable, hardy",
            exampleEn = "The resilient team adapted quickly.",
            exampleNative = "ತಂಡವು ಬೇಗನೆ ಚೇತರಿಸಿಕೊಂಡಿತು.",
            synonymsNative = "ಬಲಿಷ್ಠ, ಹೊಂದಿಕೊಳ್ಳುವ, ಗಟ್ಟಿಮುಟ್ಟಾದ",
        ),
        "Kannada",
        1_724_112_000_000L,
    ),
    RegisterEntry(
        LookupResult(
            original = "precise",
            translation = "ನಿಖರ",
            partOfSpeech = "adjective",
            meaning = "Exact and accurate.",
            synonyms = "exact, accurate, specific",
            synonymsNative = "ಸ್ಪಷ್ಟ, ನಿಷ್ಕೃಷ್ಟ",
        ),
        "Kannada",
        1_724_025_600_000L,
    ),
)

@Preview(name = "Onboarding", showBackground = true, heightDp = 900)
@Composable
private fun SetupScreenPreview() {
    WordLookupTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SetupScreen(currentLanguage = "Kannada", hasGeminiKey = false, onSave = { _, _ -> })
        }
    }
}

@Preview(name = "Home", showBackground = true, heightDp = 900)
@Composable
private fun HomePreview() {
    WordLookupTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreenPreviewLayout()
        }
    }
}

@Preview(
    name = "Home — dark",
    showBackground = true,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeDarkPreview() {
    WordLookupTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreenPreviewLayout()
        }
    }
}

/**
 * A static mirror of HomeScreen's layout. The real HomeScreen needs a live
 * MainViewModel (DataStore, EncryptedSharedPreferences, Room), none of which
 * exist in the preview renderer - so the visual structure is reproduced here
 * with literal values instead. Preview-only; never invoked by the app.
 */
@Composable
private fun HomeScreenPreviewLayout() {
    var triggerMode by remember { mutableStateOf(TriggerMode.BOTH) }
    val order = listOf(TriggerMode.INSTANT, TriggerMode.MENU_ONLY, TriggerMode.BOTH)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ProminentCard),
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        HomeHeader(glyph = "ಕ", enabled = true, onEnabledChange = {})
        SummaryCard(language = "Kannada")

        SectionCard(title = "How it opens") {
            SegmentedControl(
                options = listOf("Instant", "Menu", "Both"),
                selectedIndex = order.indexOf(triggerMode),
                onSelect = { triggerMode = order[it] },
            )
            Text(
                "The Quick Settings tile switches the instant popup on and off: that's this setting, between \"Both\" and \"Menu only\".",
                modifier = Modifier.padding(top = Spacing.SM),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NavigationCard(title = "Word register", subtitle = "Every lookup you've made, searchable", onClick = {})
        NavigationCard(title = "Settings", subtitle = "Permissions, language, API key", onClick = {})
    }
}

@Preview(name = "Settings", showBackground = true, heightDp = 900)
@Composable
private fun SettingsScreenPreview() {
    WordLookupTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
                overlayGranted = true,
                accessibilityGranted = false,
                showAddTile = true,
                onBack = {},
                onOpenOverlaySettings = {},
                onOpenAccessibilitySettings = {},
                onOpenAppInfo = {},
                onEditSetup = {},
                onAddTile = {},
                onPauseForBanking = { true },
            )
        }
    }
}

@Preview(
    name = "Settings — dark",
    showBackground = true,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsScreenDarkPreview() {
    WordLookupTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
                overlayGranted = true,
                accessibilityGranted = true,
                showAddTile = true,
                onBack = {},
                onOpenOverlaySettings = {},
                onOpenAccessibilitySettings = {},
                onOpenAppInfo = {},
                onEditSetup = {},
                onAddTile = {},
                onPauseForBanking = { true },
            )
        }
    }
}

@Preview(name = "Lookup card — result", showBackground = true)
@Composable
private fun LookupCardResultPreview() {
    // LookupCard itself is unchanged by the redesign and still reads its colors
    // from res/values/colors.xml, so this renders exactly as it ships.
    WordLookupTheme { LookupCard(CardState.Result(SAMPLE_RESULT)) }
}

@Preview(name = "Lookup card — loading", showBackground = true)
@Composable
private fun LookupCardLoadingPreview() {
    WordLookupTheme { LookupCard(CardState.Loading("ephemeral")) }
}

@Preview(name = "Lookup card — error", showBackground = true)
@Composable
private fun LookupCardErrorPreview() {
    // CardState has no distinct error type - failures surface as Message,
    // exactly as they do in production.
    WordLookupTheme { LookupCard(CardState.Message("Couldn't reach the translation service. Try again.")) }
}

/**
 * The popup and its register copy, side by side. Both must render at the
 * same size despite the register living inside WordLookupTheme and the
 * popup living inside no theme at all (OverlayHost never wraps LookupCard) -
 * this is the visual check for that type-scale trap. If register text reads
 * larger than the card beside it, the MaterialTheme(typography=Typography())
 * wrapper in RegisterScreen.RegisterEntryCard has regressed.
 */
@Preview(name = "Popup vs. register match", showBackground = true, heightDp = 620)
@Composable
private fun PopupVsRegisterPreview() {
    WordLookupTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.padding(Spacing.MD)) {
                LookupCard(CardState.Result(SAMPLE_RESULT))
                Column(Modifier.padding(start = Spacing.MD).weight(1f)) {
                    RegisterScreen(
                        entries = listOf(RegisterEntry(SAMPLE_RESULT, "Kannada", 1_724_198_400_000L)),
                        onDelete = { _, _ -> },
                        onBack = {},
                    )
                }
            }
        }
    }
}

@Preview(name = "Register — populated", showBackground = true, heightDp = 900)
@Composable
private fun RegisterPopulatedPreview() {
    WordLookupTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RegisterScreen(entries = SAMPLE_ENTRIES, onDelete = { _, _ -> }, onBack = {})
        }
    }
}

@Preview(
    name = "Register — populated, dark",
    showBackground = true,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun RegisterPopulatedDarkPreview() {
    WordLookupTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RegisterScreen(entries = SAMPLE_ENTRIES, onDelete = { _, _ -> }, onBack = {})
        }
    }
}

@Preview(name = "Register — empty", showBackground = true, heightDp = 700)
@Composable
private fun RegisterEmptyPreview() {
    WordLookupTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RegisterScreen(entries = emptyList(), onDelete = { _, _ -> }, onBack = {})
        }
    }
}
