package com.harish.wordlookup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.ui.theme.Spacing

/**
 * A permission's state as a hardware-style readout: a filled dot means on,
 * a hollow ring means off - the same vocabulary as an LED, not a coloured
 * icon standing in for one. The trailing label is monospaced-weight text
 * ("ON"/"OFF"), not a filled chip - a status word, not a decoration.
 */
@Composable
fun PermissionStatusRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Spacing.SM + Spacing.XS),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(8.dp)
                .then(
                    if (granted) {
                        Modifier.background(MaterialTheme.colorScheme.secondary, CircleShape)
                    } else {
                        Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
                    },
                )
                .semantics { contentDescription = if (granted) "Granted" else "Not granted" },
        )
        Spacer(Modifier.width(Spacing.SM + Spacing.XS))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Justify,
            )
            if (!granted) {
                TextButton(
                    onClick = onClick,
                    modifier = Modifier.heightIn(min = 48.dp).padding(top = Spacing.XS),
                ) {
                    Text(actionLabel)
                }
            }
        }
        Text(
            if (granted) "ON" else "OFF",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
