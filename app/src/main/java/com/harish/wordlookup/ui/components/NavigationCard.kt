package com.harish.wordlookup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.ui.theme.Radius
import com.harish.wordlookup.ui.theme.Spacing

/**
 * A whole-card tap target that navigates somewhere - read as one unit by
 * TalkBack. [showActivityDot] (round 10) renders a small filled dot before
 * the chevron - a genuinely binary "there is something new here" signal
 * (word register/settings never pass it), reusing [PermissionStatusRow]'s
 * granted-dot idiom rather than a new visual.
 */
@Composable
fun NavigationCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showActivityDot: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(Radius.XL),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.SM)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(Spacing.MD),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showActivityDot) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                )
                Spacer(Modifier.width(Spacing.SM))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
