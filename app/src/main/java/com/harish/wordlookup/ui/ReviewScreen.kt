package com.harish.wordlookup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.R
import com.harish.wordlookup.data.review.ReviewCard
import com.harish.wordlookup.data.review.ReviewGrade
import com.harish.wordlookup.data.review.ReviewScheduler
import com.harish.wordlookup.data.speech.SpeechAvailability
import com.harish.wordlookup.ui.components.SpeakButton
import com.harish.wordlookup.ui.theme.Radius
import com.harish.wordlookup.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The quiz, entered only from the register's bottom dock (see
 * RegisterScreen.kt) - three states, per the round-8 mockup:
 * prompt -> revealed -> (next card, or) done. [onGrade] advances the
 * session; the caller (AppRoot) is responsible for loading the batch via
 * MainViewModel.startReview() before this screen is shown.
 *
 * The whole screen scrolls (matching Settings/Register elsewhere in the
 * app) rather than trying to pin the card to a fixed centered region within
 * a weighted middle area. That weight()+Arrangement.Center approach was
 * tried first and reverted: on the emulator it produced a stable,
 * reproducible garbled overlap once the revealed card (six-plus stacked
 * text lines) exceeded the space available - Center's negative offset for
 * an over-tall child drew every row on top of the last instead of
 * overflowing top or bottom. A plain top-to-bottom scrolling Column has no
 * such failure mode: every element gets its own natural height, always.
 */
@Composable
fun ReviewScreen(
    session: ReviewSession,
    onGrade: (ReviewGrade) -> Unit,
    onBack: () -> Unit,
    loadNextDueAt: suspend () -> Long?,
) {
    val card = session.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ProminentCard),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            if (session.total > 0) {
                Text(
                    "${session.position} of ${session.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            card != null -> ReviewCardContent(card, onGrade)
            else -> ReviewDone(loadNextDueAt, onBack)
        }
    }
}

@Composable
private fun ReviewCardContent(card: ReviewCard, onGrade: (ReviewGrade) -> Unit) {
    var revealed by remember(card) { mutableStateOf(false) }

    if (!revealed) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = Spacing.XL * 2),
            contentAlignment = Alignment.Center,
        ) {
            PromptContent(card)
        }
        Button(
            onClick = { revealed = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(Radius.LG),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Text("Show answer")
        }
    } else {
        RevealedContent(card)
        GradeRow(card, onGrade)
    }
}

@Composable
private fun PromptContent(card: ReviewCard) {
    val speech = rememberCardSpeech(card.result, languageName = card.language)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.result.original, style = MaterialTheme.typography.headlineLarge)
            if (speech.english.availability != SpeechAvailability.NOT_SUPPORTED) {
                Spacer(Modifier.size(Spacing.XS))
                SpeakButton(
                    availability = speech.english.availability,
                    onSpeak = speech.english.onSpeak,
                    onInstallVoice = speech.english.onInstallVoice,
                    contentDescriptionLabel = "Speak \"${card.result.original}\" in English",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (card.result.partOfSpeech.isNotBlank()) {
            Text(
                card.result.partOfSpeech,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.XS),
            )
        }
    }
}

/**
 * The same card body a third time - [LookupResultBody] again, wrapped
 * exactly as RegisterScreen.RegisterEntryCard wraps it (gradient box,
 * `MaterialTheme(typography = Typography())` so it resolves against the
 * same stock M3 scale the popup gets from OverlayHost's themeless host)
 * rather than routing through [LookupCard], whose fixed 320dp width would
 * fight this screen's fillMaxWidth layout. Naturally sized, top-aligned, no
 * forced height - the same proven-correct pattern the register already
 * uses for the identical wrapping.
 */
@Composable
private fun RevealedContent(card: ReviewCard) {
    val speech = rememberCardSpeech(card.result, languageName = card.language)
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
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
                LookupResultBody(card.result, speech = speech)
            }
        }
    }
}

@Composable
private fun GradeRow(card: ReviewCard, onGrade: (ReviewGrade) -> Unit) {
    val now = remember(card) { System.currentTimeMillis() }
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.MD),
        horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
    ) {
        GradeButton(
            label = "Again",
            sublabel = intervalLabel(ReviewGrade.AGAIN, card, now),
            emphasized = false,
            modifier = Modifier.weight(1f),
            onClick = { onGrade(ReviewGrade.AGAIN) },
        )
        GradeButton(
            label = "Good",
            sublabel = intervalLabel(ReviewGrade.GOOD, card, now),
            emphasized = true,
            modifier = Modifier.weight(1f),
            onClick = { onGrade(ReviewGrade.GOOD) },
        )
        GradeButton(
            label = "Easy",
            sublabel = intervalLabel(ReviewGrade.EASY, card, now),
            emphasized = false,
            modifier = Modifier.weight(1f),
            onClick = { onGrade(ReviewGrade.EASY) },
        )
    }
}

@Composable
private fun GradeButton(
    label: String,
    sublabel: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.LG),
        color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier.height(56.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasized) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** "10 min" for AGAIN (its delay is a fixed constant); otherwise the computed interval, singular/plural. */
private fun intervalLabel(grade: ReviewGrade, card: ReviewCard, now: Long): String {
    if (grade == ReviewGrade.AGAIN) return "10 min"
    val outcome = ReviewScheduler.preview(card.schedule, grade, now)
    val days = outcome.state.intervalDays
    return if (days == 1) "1 day" else "$days days"
}

@Composable
private fun ReviewDone(loadNextDueAt: suspend () -> Long?, onBack: () -> Unit) {
    var nextDueAt by remember { mutableStateOf<Long?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        nextDueAt = loadNextDueAt()
        loaded = true
    }

    Box(
        Modifier.fillMaxWidth().padding(vertical = Spacing.XL * 2),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            title = "All caught up",
            body = when {
                loaded && nextDueAt != null -> "More words are due ${formatNextDue(nextDueAt!!)}."
                loaded -> "Nothing else is due for now."
                else -> ""
            },
        )
    }
    Button(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(Radius.LG),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text("Back to your words")
    }
}

private fun formatNextDue(epochMillis: Long): String {
    val daysAway = (epochMillis - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)
    return when {
        daysAway <= 0 -> "shortly"
        daysAway == 1L -> "tomorrow"
        daysAway < 7 -> "on ${SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(epochMillis))}"
        else -> "on ${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMillis))}"
    }
}
