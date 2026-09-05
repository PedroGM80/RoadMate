package dev.pgm.roadmate.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pgm.roadmate.ui.theme.IconSize
import dev.pgm.roadmate.ui.theme.Spacing

/**
 * The rounded "icon + short label" pill used for glanceable status (the
 * location chip, the offline-map state). One component instead of a
 * near-identical copy on each screen. When [onClick] is set the whole pill
 * gets a 48dp minimum touch target.
 */
@Composable
fun InfoPill(
    label: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = if (onClick != null) {
            modifier.defaultMinSize(minHeight = Spacing.touchTarget)
        } else {
            modifier
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.md - Spacing.xs, vertical = Spacing.sm)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(painter = icon, contentDescription = null, modifier = Modifier.size(IconSize.sm))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = if (icon != null) Spacing.xs else Spacing.none),
            )
        }
    }
}
