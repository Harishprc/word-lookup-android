package com.harish.wordlookup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.R
import com.harish.wordlookup.data.speech.SpeechAvailability

/**
 * A speaker on the lookup card. Lives beside a headword or translation, so
 * it stays inside the card's own five-step grey ladder ([tint] defaults to
 * `pos_text`, matching the other secondary marks on the card) rather than
 * importing app-theme colour into a composable that deliberately doesn't
 * use one - see LookupCard.kt's own header comment.
 *
 * Never rendered when [availability] is NOT_SUPPORTED - a control with
 * nothing it can ever do is worse than no control. When MISSING_DATA, the
 * button stays live and tapping it asks the OS to install voice data
 * instead of speaking - [R.color.accent] (declared in colors.xml, unused
 * everywhere else in the app today) marks that state with a small dot,
 * reusing an existing recovered token rather than adding a new one to a
 * file this app keeps deliberately unmodified.
 */
@Composable
fun SpeakButton(
    availability: SpeechAvailability,
    onSpeak: () -> Unit,
    onInstallVoice: () -> Unit,
    contentDescriptionLabel: String,
    modifier: Modifier = Modifier,
    tint: Color = colorResource(R.color.pos_text),
) {
    if (availability == SpeechAvailability.NOT_SUPPORTED) return

    Box(modifier.size(26.dp), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = {
                if (availability == SpeechAvailability.MISSING_DATA) onInstallVoice() else onSpeak()
            },
            modifier = Modifier
                .size(26.dp)
                .semantics { contentDescription = contentDescriptionLabel },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        if (availability == SpeechAvailability.MISSING_DATA) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(6.dp)
                    .background(colorResource(R.color.accent), CircleShape),
            )
        }
    }
}
