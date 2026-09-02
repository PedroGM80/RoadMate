package dev.pgm.roadmate.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.pgm.roadmate.R
import dev.pgm.roadmate.presentation.viewmodel.RoadMateStatus
import dev.pgm.roadmate.presentation.viewmodel.RoadMateUiState
import dev.pgm.roadmate.ui.theme.Spacing

/**
 * The card that shows what was heard and what RoadMate answered. It pops
 * (scale + a confirm haptic) when a fresh answer lands, so the arrival is
 * felt even if the driver's eyes are on the road. An error/nudge is drawn
 * in the error colour, not the reply green.
 */
@Composable
fun AnswerCard(
    uiState: RoadMateUiState,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(uiState.status) {
        if (uiState.status == RoadMateStatus.SPEAKING) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            if (!reduceMotion) {
                scale.snapTo(0.96f)
                scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
        }
    }

    val listening = uiState.status == RoadMateStatus.LISTENING ||
        uiState.status == RoadMateStatus.FOLLOW_UP
    val body = when {
        uiState.currentResponse.isNotBlank() -> uiState.currentResponse
        listening && uiState.lastRecognizedInput.isBlank() -> stringResource(R.string.home_listening)
        listening -> ""
        else -> stringResource(R.string.home_prompt_idle)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().scale(scale.value),
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.lg)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            if (uiState.lastRecognizedInput.isNotBlank()) {
                Text(
                    text = stringResource(R.string.home_recognized_quote, uiState.lastRecognizedInput),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            if (body.isNotEmpty()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        uiState.currentResponse.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                        uiState.isError -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                )
            }
        }
    }
}
