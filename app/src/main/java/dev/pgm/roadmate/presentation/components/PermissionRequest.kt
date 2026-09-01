package dev.pgm.roadmate.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pgm.roadmate.R
import dev.pgm.roadmate.ui.theme.Spacing

/** Shown in place of the mic when the core permissions aren't granted yet. */
@Composable
fun PermissionRequest(
    shouldShowRationale: Boolean,
    onRequestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(R.drawable.lucide_ic_mic),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(
                if (shouldShowRationale) R.string.permission_rationale else R.string.permission_request,
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm + Spacing.xs, bottom = Spacing.lg - Spacing.xs),
        )
        Button(onClick = onRequestClick) { Text(stringResource(R.string.permission_grant)) }
    }
}
