package dev.pgm.roadmate.presentation.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.pgm.roadmate.ui.theme.Spacing

/**
 * The one control the app is really about. Tap to start/stop; a short earcon
 * and a toggle haptic confirm it. Idle = amber (the CTA); listening = the
 * "active" secondary-container tone with a stop glyph — deliberately not the
 * red error colour, which would read as danger. The breathing pulse and the
 * waveform are decorative and hold still under reduce-motion.
 */
@Composable
fun MicButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { toneGenerator?.release() } }

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
        if (isListening) VoiceWaveform(reduceMotion, Modifier.padding(bottom = Spacing.lg - Spacing.xs))

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
                    toneGenerator?.startTone(
                        if (isListening) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP,
                        120,
                    )
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
private fun VoiceWaveform(reduceMotion: Boolean, modifier: Modifier = Modifier, barCount: Int = 5) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(barCount) { index -> WaveformBar(index, reduceMotion) }
    }
}

@Composable
private fun WaveformBar(index: Int, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave-$index")
    val fraction by transition.animateFloat(
        initialValue = if (reduceMotion) 0.6f else 0.3f,
        targetValue = if (reduceMotion) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            tween(380 + index * 70, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
            StartOffset(index * 90),
        ),
        label = "wave-bar-$index",
    )
    Box(
        modifier = Modifier
            .width(5.dp)
            .height(28.dp * fraction)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondary),
    )
}
