package com.harish.wordlookup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    SegmentedTrack(count = options.size, selectedIndex = selectedIndex, onSelect = onSelect, modifier = modifier) { index, selected ->
        Text(
            options[index],
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.SM),
        )
    }
}

/**
 * The same control, rendering an icon per segment instead of a word - for a
 * label that would rather not fit ("Afternoon" wrapped to two lines as
 * text, at this width). [contentDescriptions] carries what the word used to
 * say, so TalkBack still announces "Afternoon, selected" exactly as before;
 * dropping the word from the screen never drops it from the accessibility
 * tree.
 */
@Composable
fun IconSegmentedControl(
    icons: List<ImageVector>,
    contentDescriptions: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(icons.size == contentDescriptions.size) {
        "icons and contentDescriptions must be the same length (${icons.size} vs ${contentDescriptions.size})"
    }
    SegmentedTrack(
        count = icons.size,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        contentDescriptionFor = { index -> contentDescriptions[index] },
    ) { index, selected ->
        Icon(
            icons[index],
            contentDescription = null, // the segment's own selectable() carries the description below
            tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The track/segment/selection chrome shared by both variants above - one
 * definition of the recessed-track-plus-raised-key visual, so a future
 * third variant (or a tweak to this one) can't drift the two apart. Content
 * is late-bound so the caller decides whether a segment shows a word or an
 * icon; everything about hit target, selection state, and semantics is
 * identical either way.
 */
@Composable
private fun SegmentedTrack(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionFor: (Int) -> String? = { null },
    content: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    val segmentShape = RoundedCornerShape(11.dp)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(3.dp).selectableGroup()) {
            for (index in 0 until count) {
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
                segmentModifier = segmentModifier.selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(index) },
                )
                contentDescriptionFor(index)?.let { label ->
                    segmentModifier = segmentModifier.semantics { contentDescription = label }
                }
                Box(segmentModifier, contentAlignment = Alignment.Center) {
                    content(index, selected)
                }
            }
        }
    }
}
