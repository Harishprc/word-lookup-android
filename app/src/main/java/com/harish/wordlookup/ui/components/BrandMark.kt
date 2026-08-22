package com.harish.wordlookup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harish.wordlookup.ui.theme.Radius

/**
 * The app's mark: an ink rounded square carrying the active language's own
 * glyph (from [com.harish.wordlookup.data.Language.glyph]) rather than a
 * generic icon - the same mark that drives the Quick Settings tile and the
 * launcher icon, so all three read as one identity. Decorative in context
 * (the app name or a language label always sits beside it), so no
 * contentDescription. Gradient + glow to match [MainActivity]'s SummaryCard,
 * the other ink-filled surface in the app.
 */
@Composable
fun BrandMark(glyph: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val ink = MaterialTheme.colorScheme.onSurface
    val inkDeep = lerp(ink, Color.Black, 0.28f)
    val onInk = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(Radius.LG)

    Surface(
        shape = shape,
        color = ink,
        shadowElevation = size / 6,
        modifier = modifier.size(size),
    ) {
        Box(
            Modifier.background(Brush.linearGradient(listOf(ink, inkDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                glyph,
                color = onInk,
                fontSize = (size.value * 0.5f).sp,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
