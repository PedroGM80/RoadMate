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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pgm.roadmate.R
import dev.pgm.roadmate.audio.Earcon
import dev.pgm.roadmate.ui.theme.Spacing

/**
 * The one control the app is really about. Tap to start/stop; a short earcon
 * and a toggle haptic confirm it. Idle = amber (the CTA); listening = the
 * "active" secondary-container tone with a stop glyph — deliberately not the
 * red error colour, which would read as danger. A single breathing dot marks
 * "listening" — no fake waveform, since nothing here actually taps the mic
 * amplitude. The pulse and the dot hold still under reduce-motion.
 */
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
        if (isListening) {
            ListeningDot(reduceMotion, Modifier.padding(bottom = Spacing.lg - Spacing.xs))
        }

        Box(contentAlignment = Alignment.Center) {
            if (isListening && !reduceMotion) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
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
                modifier = Modifier.size(80.dp).scale(pressScale),
            ) {
                Icon(
                    painter = painterResource(
                        if (isListening) R.drawable.lucide_ic_square else R.drawable.lucide_ic_mic,
                    ),
                    contentDescription = stringResource(
                        if (isListening) R.string.mic_stop else R.string.mic_start,
                    ),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun ListeningDot(reduceMotion: Boolean, modifier: Modifier = Modifier) {
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
            .size(10.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary),
    )
}
