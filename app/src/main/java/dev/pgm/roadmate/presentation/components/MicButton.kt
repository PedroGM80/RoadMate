package dev.pgm.roadmate.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pgm.roadmate.R
import dev.pgm.roadmate.audio.Earcon
import dev.pgm.roadmate.ui.theme.Spacing
import dev.pgm.roadmate.ui.theme.Dimens
import dev.pgm.roadmate.ui.theme.IconSize
import androidx.compose.ui.graphics.graphicsLayer


@Composable
fun MicButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val earcon = remember { Earcon() }
    DisposableEffect(Unit) { onDispose { earcon.release() } }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "mic-press-scale",
    )

    val pulse = rememberInfiniteTransition(label = "mic-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "mic-pulse-scale",
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {

        ListeningDot(
            visible = isListening,
            reduceMotion = reduceMotion,
            modifier = Modifier.padding(bottom = Spacing.lg - Spacing.xs),
        )

        Box(contentAlignment = Alignment.Center) {
            if (isListening && !reduceMotion) {
                Box(
                    modifier = Modifier
                        .size(Dimens.micPulseRing)
                        .scale(pulseScale)
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                            CircleShape,
                        ),
                )
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(
                        if (isListening) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                    if (isListening) earcon.stop() else earcon.start()
                    onClick()
                },
                shape = CircleShape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    contentColor = if (isListening) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondary
                    },
                ),
                modifier = Modifier.size(Dimens.micButton).graphicsLayer {
                    scaleX = pressScale; scaleY = pressScale
                },
            ) {
                Icon(
                    painter = painterResource(
                        if (isListening) R.drawable.lucide_ic_square else R.drawable.lucide_ic_mic,
                    ),
                    contentDescription = stringResource(
                        if (isListening) R.string.mic_stop else R.string.mic_start,
                    ),
                    modifier = Modifier.size(IconSize.xl),
                )
            }
        }
    }
}

@Composable
private fun ListeningDot(
    visible: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "listening-dot")
    val scale by transition.animateFloat(
        initialValue = if (reduceMotion) 1f else 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "listening-dot-scale",
    )
    Box(
        modifier = modifier
            .size(Dimens.listeningDot)
            .scale(if (visible) scale else 1f)
            .clip(CircleShape)
            .background(
                if (visible) MaterialTheme.colorScheme.secondary else Color.Transparent,
            ),
    )
}
