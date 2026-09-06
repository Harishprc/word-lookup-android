package com.harish.wordlookup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.R
import com.harish.wordlookup.data.LookupResult
import com.harish.wordlookup.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Round 10: entirely local, hardcoded data - not the real lookup path. No
 * Gemini call, no network, no schema change (the sentence word-by-word
 * breakdown here is static demo content, the same way the mockup's own
 * `breakdown` array was). Reuses [LookupCard]/[LookupResultBody] for the
 * word card (round 6's "same object, not a second layout" rule); the
 * sentence card is bespoke since sentence-level breakdown isn't part of
 * [LookupResult]'s shape.
 */
@Composable
fun TryLookupScreen(onBack: () -> Unit) {
    var wordCardOpen by remember { mutableStateOf(false) }
    var sentenceCardOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    LaunchedEffect(wordCardOpen) {
        if (wordCardOpen) {
            delay(6000)
            wordCardOpen = false
        }
    }

    fun dismiss() {
        wordCardOpen = false
        sentenceCardOpen = false
        expanded = false
        dragOffsetPx = 0f
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().padding(Spacing.ProminentCard)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(Spacing.SM))
                    Text("Try a lookup", style = MaterialTheme.typography.headlineMedium)
                }

                val paragraph = buildAnnotatedString {
                    append("The committee accepted the proposal on the strength of a single argument. The ")
                    pushStringAnnotation(tag = WORD_TAG, annotation = "ostensible")
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append("ostensible")
                    }
                    pop()
                    append(" justification was cost.")
                }
                ClickableText(
                    text = paragraph,
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.padding(top = Spacing.MD),
                    onClick = { offset ->
                        paragraph.getStringAnnotations(WORD_TAG, offset, offset).firstOrNull()?.let {
                            dismiss()
                            wordCardOpen = true
                        }
                    },
                )

                Text(
                    "Later revisions inherited the framing without revisiting it, which is how an assumption quietly becomes a requirement.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = Spacing.MD)
                        .clickable {
                            dismiss()
                            sentenceCardOpen = true
                        },
                )

                Text(
                    when {
                        sentenceCardOpen && expanded -> "Expanded, it stays open now. Tap outside to dismiss."
                        sentenceCardOpen -> "Drag the card down to break the sentence into words."
                        wordCardOpen -> "Six seconds, then it leaves on its own. Tap it to dismiss now."
                        else -> "Tap the underlined word, or the sentence below it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.MD),
                )
            }

            if (wordCardOpen) {
                Box(
                    Modifier.fillMaxSize().clickable { dismiss() },
                    contentAlignment = Alignment.TopStart,
                ) {
                    MaterialTheme(typography = Typography()) {
                        LookupCard(
                            state = CardState.Result(DEMO_WORD_RESULT),
                            modifier = Modifier
                                .padding(start = Spacing.ProminentCard, top = 96.dp)
                                .clickable { dismiss() },
                        )
                    }
                }
            }

            if (sentenceCardOpen) {
                Box(Modifier.fillMaxSize().clickable { dismiss() })
                Box(
                    Modifier
                        .padding(start = Spacing.ProminentCard, top = 120.dp)
                        .offset { IntOffset(0, if (expanded) 0 else dragOffsetPx.roundToInt().coerceAtMost(26)) }
                        .width(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(colorResource(R.color.card_top), colorResource(R.color.card_bottom)),
                            ),
                        )
                        .then(
                            if (expanded) {
                                Modifier
                            } else {
                                Modifier.draggable(
                                    orientation = Orientation.Vertical,
                                    state = rememberDraggableState { delta ->
                                        dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
                                    },
                                    onDragStopped = {
                                        if (dragOffsetPx > 44f) expanded = true
                                        dragOffsetPx = 0f
                                    },
                                )
                            },
                        ),
                ) {
                    SentenceDemoCard(expanded = expanded)
                }
            }
        }
    }
}

@Composable
private fun SentenceDemoCard(expanded: Boolean) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Sentence", color = colorResource(R.color.word_text), style = MaterialTheme.typography.headlineSmall)
            Text(
                "  · 17 words",
                color = colorResource(R.color.pos_text),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "Later revisions inherited the framing without revisiting it, which is how an assumption quietly becomes a requirement.",
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
            color = colorResource(R.color.example_text),
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colorResource(R.color.divider))
        Text(
            "ನಂತರದ ಪರಿಷ್ಕರಣೆಗಳು ಆ ಚೌಕಟ್ಟನ್ನು ಮರುಪರಿಶೀಲಿಸದೆ ಮುಂದುವರಿಸಿದವು.",
            color = colorResource(R.color.translation_text),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (expanded) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colorResource(R.color.divider))
            Text(
                "WORD BY WORD",
                style = MaterialTheme.typography.labelSmall,
                color = colorResource(R.color.pos_text),
                modifier = Modifier.padding(bottom = 10.dp),
            )
            SENTENCE_BREAKDOWN.forEach { (en, pos, kn) ->
                Row(Modifier.padding(bottom = Spacing.SM)) {
                    Column(Modifier.width(104.dp)) {
                        Text(en, style = MaterialTheme.typography.bodyMedium, color = colorResource(R.color.word_text))
                        Text(pos, style = MaterialTheme.typography.bodySmall, color = colorResource(R.color.pos_text))
                    }
                    Text(kn, color = colorResource(R.color.translation_text), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorResource(R.color.divider)),
                )
                Text(
                    "pull down for word by word",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorResource(R.color.pos_text),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private const val WORD_TAG = "word"

private val DEMO_WORD_RESULT = LookupResult(
    original = "ostensible",
    translation = "ಹೊರತೋರಿಕೆಯ",
    partOfSpeech = "adjective",
    meaning = "Stated or appearing to be true, but not necessarily so.",
    synonyms = "apparent, supposed, alleged",
    exampleEn = "The ostensible reason for his visit was to see the museum.",
    exampleNative = "ಅವನ ಭೇಟಿಯ ಹೊರತೋರಿಕೆಯ ಕಾರಣ ವಸ್ತುಸಂಗ್ರಹಾಲಯವನ್ನು ನೋಡುವುದಾಗಿತ್ತು.",
    synonymsNative = "ಕಾಣಿಸುವ, ಭಾಸದ",
)

private val SENTENCE_BREAKDOWN = listOf(
    Triple("revisions", "noun", "ಪರಿಷ್ಕರಣೆಗಳು"),
    Triple("inherited", "verb", "ಮುಂದುವರಿಸಿದವು"),
    Triple("framing", "noun", "ಚೌಕಟ್ಟು"),
    Triple("assumption", "noun", "ಊಹೆ"),
    Triple("requirement", "noun", "ಅವಶ್ಯಕತೆ"),
)
