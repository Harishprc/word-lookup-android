package com.harish.wordlookup.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.ui.theme.Radius
import com.harish.wordlookup.ui.theme.Spacing

/**
 * The app's standard grouping surface: a white card lifted by a soft shadow,
 * no hairline - the later monochrome pass moved from a bordered-flat system
 * to a shaded/shadowed one. `prominent` swaps the padding up to 24dp for the
 * cards that lead a screen; `containerColor` lets the home summary card
 * carry a solid ink fill instead.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shadowElevation: Dp = 4.dp,
    prominent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.XL),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.SM),
    ) {
        Column(Modifier.padding(if (prominent) Spacing.ProminentCard else Spacing.MD)) {
            if (title != null) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = Spacing.SM),
                )
            }
            content()
        }
    }
}
