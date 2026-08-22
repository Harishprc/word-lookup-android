package com.harish.wordlookup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.ui.theme.Spacing

/**
 * Single-select segmented control - the modern stand-in for a short
 * RadioButton list. The selected segment reads as a raised key on a
 * recessed track (surface + hairline + heavier weight), not a colour fill -
 * value and weight carry the state, not the accent. Each segment keeps
 * [Role.RadioButton] semantics: still single-select, so TalkBack announces
 * selected/not-selected exactly as the RadioButton rows it replaces did.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segmentShape = RoundedCornerShape(11.dp)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(3.dp).selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                var segmentModifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(segmentShape)
                if (selected) {
                    segmentModifier = segmentModifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), segmentShape)
                }
                Box(
                    segmentModifier.selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Spacing.SM),
                    )
                }
            }
        }
    }
}
