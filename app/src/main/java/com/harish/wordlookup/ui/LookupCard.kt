package com.harish.wordlookup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.R
import com.harish.wordlookup.data.LookupResult
import com.harish.wordlookup.data.speech.SpeechAvailability
import com.harish.wordlookup.ui.components.SpeakButton
import com.harish.wordlookup.ui.theme.BodyNative

/**
 * The floating dictionary card - same content in both trigger paths
 * (ProcessTextActivity's popup and SelectionAccessibilityService's overlay).
 * Untouched by the monochrome repalette: still reads res/values/colors.xml
 * directly, still 320dp wide. Its own five-step grey ladder already reads as
 * value-encoded hierarchy, so it needed nothing. The 6dp/clip=false shadow
 * matches RegisterEntryCard's own shadow exactly (RegisterScreen.kt) - same
 * object, same elevation, not two cards that happen to look similar.
 */
@Composable
fun LookupCard(state: CardState, modifier: Modifier = Modifier, speech: CardSpeech? = null) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .width(320.dp)
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(colorResource(R.color.card_top), colorResource(R.color.card_bottom)),
                ),
            )
            .padding(16.dp),
    ) {
        when (state) {
            is CardState.Loading -> LoadingContent(state.wordHint)
            is CardState.Result -> LookupResultBody(state.result, speech = speech)
            is CardState.Message -> Text(state.text, color = colorResource(R.color.meaning_text))
        }
    }
}

@Composable
private fun LoadingContent(wordHint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
        if (wordHint.isNotBlank()) {
            Text(
                wordHint,
                modifier = Modifier.padding(start = 12.dp),
                color = colorResource(R.color.word_text),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * One card's worth of speak actions - a plain data holder, no Context or
 * Speaker reference inside it, so LookupResultBody stays a pure UI
 * composable (previewable with no fake) and every existing call site
 * ([LookupCard]'s default, every `@Preview`) compiles unchanged by leaving
 * [speech] null. Callers ([OverlayHost] via the accessibility/menu paths,
 * [RegisterScreen], the round-8 review screen) build one from
 * `WordLookupApp.speaker` plus the current language's [com.harish.wordlookup.data.speech.TtsLocales] entry.
 */
data class CardSpeech(val english: SpeechAction, val native: SpeechAction)

/** [onInstallVoice] only fires anything when [availability] is MISSING_DATA - see [SpeakButton]. */
data class SpeechAction(
    val availability: SpeechAvailability,
    val onSpeak: () -> Unit,
    val onInstallVoice: () -> Unit,
)

/**
 * The card's content, extracted so the word register can render each saved
 * lookup as this exact card rather than a second, different-looking layout.
 *
 * Caller responsibility, and the one detail that makes the two actually
 * match: [LookupCard] is hosted by `OverlayHost`, which applies no
 * `MaterialTheme` at all, so every `MaterialTheme.typography.*` reference
 * below resolves against Compose's stock M3 scale (headline-small 24sp,
 * body-large 16sp, etc.) - not [com.harish.wordlookup.ui.theme.WordLookupTypography].
 * A caller rendering this inside `WordLookupTheme` (the register does) must
 * wrap it in `MaterialTheme(typography = androidx.compose.material3.Typography())`
 * or the two cards will render at different sizes despite sharing this code.
 */
@Composable
internal fun LookupResultBody(
    result: LookupResult,
    emphasizeWordAndTranslation: Boolean = false,
    speech: CardSpeech? = null,
) {
    val emphasis = if (emphasizeWordAndTranslation) FontWeight.Bold else null
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            result.original,
            color = colorResource(R.color.word_text),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = emphasis,
        )
        if (result.partOfSpeech.isNotBlank()) {
            Text(
                "  · ${result.partOfSpeech}",
                color = colorResource(R.color.pos_text),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        speech?.let {
            SpeakButton(
                availability = it.english.availability,
                onSpeak = it.english.onSpeak,
                onInstallVoice = it.english.onInstallVoice,
                contentDescriptionLabel = "Speak \"${result.original}\" in English",
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
    if (result.meaning.isNotBlank()) {
        Text(
            result.meaning,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            color = colorResource(R.color.meaning_text),
        )
    }
    if (result.synonyms.isNotBlank()) {
        Text(
            "(${result.synonyms})",
            modifier = Modifier.padding(top = 2.dp).fillMaxWidth(),
            color = colorResource(R.color.synonyms_text),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (result.exampleEn.isNotBlank()) {
        Text(
            result.exampleEn,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
            color = colorResource(R.color.example_text),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colorResource(R.color.divider))
    // The translation is the single most important word on the card, so it
    // gets DESIGN.md's body-lg treatment - bigger and looser than the
    // headword above it - rather than reusing headlineSmall like word_text.
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            result.translation,
            modifier = Modifier.wrapContentWidth(),
            color = colorResource(R.color.translation_text),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = emphasis,
        )
        if (result.synonymsNative.isNotBlank()) {
            Text(
                "  (${result.synonymsNative})",
                color = colorResource(R.color.pos_text),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        speech?.let {
            SpeakButton(
                availability = it.native.availability,
                onSpeak = it.native.onSpeak,
                onInstallVoice = it.native.onInstallVoice,
                contentDescriptionLabel = "Speak \"${result.translation}\"",
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
    if (result.exampleNative.isNotBlank()) {
        Text(
            result.exampleNative,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            color = colorResource(R.color.example_text),
            style = BodyNative,
        )
    }
}
