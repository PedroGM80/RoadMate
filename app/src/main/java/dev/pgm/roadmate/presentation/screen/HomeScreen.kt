package dev.pgm.roadmate.presentation.screen

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import dev.pgm.roadmate.presentation.viewmodel.RoadMateStatus
import dev.pgm.roadmate.presentation.viewmodel.RoadMateUiState
import dev.pgm.roadmate.presentation.viewmodel.RoadMateViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: RoadMateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionsState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
    )
    // Notifications are requested separately and never gate the mic button —
    // denying them just means the background silence-detection notification
    // (see SilenceDetectionForegroundService) won't be visible, which doesn't
    // affect the core voice Q&A flow.
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.startSilenceMonitoring()
            viewModel.refreshLocation()
            if (notificationPermissionState?.status?.isGranted == false) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "RoadMate",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        LocationChip(
            location = uiState.location,
            unavailable = uiState.locationUnavailable,
            onRetry = viewModel::refreshLocation,
            modifier = Modifier.padding(top = 8.dp)
        )

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (permissionsState.allPermissionsGranted) {
                GrantedContent(uiState = uiState, onMicClick = {
                    if (uiState.isListening) viewModel.cancelListening() else viewModel.startListening()
                })
            } else {
                PermissionRequest(
                    shouldShowRationale = permissionsState.shouldShowRationale,
                    onRequestClick = { permissionsState.launchMultiplePermissionRequest() }
                )
            }
        }
    }
}

@Composable
private fun LocationChip(
    location: Pair<Double, Double>?,
    unavailable: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when {
        location != null -> "%.4f, %.4f".format(location.first, location.second)
        unavailable -> "Ubicación no disponible · toca para reintentar"
        else -> "Buscando ubicación..."
    }

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
            .let { if (unavailable) it.clickable(onClick = onRetry) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun GrantedContent(
    uiState: RoadMateUiState,
    onMicClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (uiState.status != RoadMateStatus.IDLE) {
            Text(
                text = statusLabel(uiState.status),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    // Announce to TalkBack when the answer arrives — the driver
                    // may be looking at the road, not the screen, when it does.
                    .semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                if (uiState.lastRecognizedInput.isNotBlank()) {
                    Text(
                        text = "Tú: “${uiState.lastRecognizedInput}”",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = uiState.currentResponse.ifBlank { "Pulsa el micrófono y haz tu pregunta." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (uiState.currentResponse.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
            }
        }

        MicButton(isListening = uiState.isListening, onClick = onMicClick)
    }
}

@Composable
private fun MicButton(isListening: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // M3 Expressive-style tactile press: a bouncy spring instead of a linear
    // scale, so the button feels squeezed rather than just resized.
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mic-press-scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "mic-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic-pulse-scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isListening) {
            VoiceWaveform(modifier = Modifier.padding(bottom = 20.dp))
        }

        Box(contentAlignment = Alignment.Center) {
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(pulseScale)
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                )
            }

            Button(
                onClick = onClick,
                shape = CircleShape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    contentColor = Color.White
                ),
                modifier = Modifier.size(80.dp).scale(pressScale)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isListening) "Detener" else "Escuchar",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Animated bars suggesting sound while listening — visual flourish, not a
 * real amplitude readout. SpeechRecognitionManager's onRmsChanged callback
 * (currently unused) could drive real mic-reactive bar heights later if
 * that's worth the plumbing through SpeechRecognitionRepository.
 */
@Composable
private fun VoiceWaveform(modifier: Modifier = Modifier, barCount: Int = 5) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index -> WaveformBar(index = index) }
    }
}

@Composable
private fun WaveformBar(index: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave-$index")
    val heightFraction by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380 + index * 70, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(index * 90)
        ),
        label = "wave-bar-$index"
    )

    Box(
        modifier = Modifier
            .width(5.dp)
            .height(28.dp * heightFraction)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondary)
    )
}

@Composable
private fun PermissionRequest(shouldShowRationale: Boolean, onRequestClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = if (shouldShowRationale) {
                "RoadMate necesita el micrófono y la ubicación para responder tus preguntas."
            } else {
                "Concede permisos de micrófono y ubicación para empezar."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
        )
        Button(onClick = onRequestClick) {
            Text("Conceder permisos")
        }
    }
}

private fun statusLabel(status: RoadMateStatus): String = when (status) {
    RoadMateStatus.IDLE -> ""
    RoadMateStatus.LISTENING -> "ESCUCHANDO..."
    RoadMateStatus.PROCESSING -> "PROCESANDO..."
    RoadMateStatus.SPEAKING -> "RESPONDIENDO"
}
