package dev.pgm.roadmate.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pgm.roadmate.R
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.ui.theme.Spacing
import dev.pgm.roadmate.ui.theme.Dimens

/**
 * One small line under the title telling the driver, honestly, where the
 * on-device AI stands — ready, downloading (with a bar), waiting for Wi-Fi,
 * failed (with retry), or "modo básico" for good. So generic fallback
 * answers aren't a mystery.
 */
@Composable
fun LocalAiStatusLabel(
    status: LocalAiStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (status) {
            LocalAiStatus.ReadyAicore, LocalAiStatus.ReadyLocalModel ->
                Line(stringResource(R.string.ai_ready), scheme.tertiary)

            LocalAiStatus.Checking ->
                Line(stringResource(R.string.ai_checking), scheme.onSurfaceVariant)

            LocalAiStatus.ModelDownloadable ->
                Line(stringResource(R.string.ai_preparing_download), scheme.onSurfaceVariant)

            is LocalAiStatus.Downloading -> {
                Line(
                    stringResource(R.string.ai_downloading, (status.progress * 100).toInt()),
                    scheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { status.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.padding(top = Spacing.xs).width(Dimens.statusLabelMaxWidth),
                )
            }

            LocalAiStatus.WaitingForWifi ->
                Line(stringResource(R.string.ai_waiting_wifi), scheme.onSurfaceVariant)

            is LocalAiStatus.DownloadFailed -> {
                Line(stringResource(R.string.ai_download_failed), scheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }

            LocalAiStatus.Unavailable ->
                Line(stringResource(R.string.ai_unavailable), scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Line(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}
