package dev.pgm.roadmate.presentation.screen

import android.Manifest
import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import dev.pgm.roadmate.domain.model.LocalAiStatus
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
    // Also secondary and non-blocking: without it, "llama a X" just falls
    // back to GenerateResponseUseCase's "no tengo permiso" spoken response
    // instead of silently failing — the core voice Q&A flow never depends on it.
    val callPermissionsState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
    )

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.startSilenceMonitoring()
            viewModel.refreshLocation()
            viewModel.greetIfNeeded()
            // Android only allows one permission dialog in flight at a time
            // ("Can request only one set of permissions at a time") — firing
            // both here would silently drop the second request. So: ask for
            // notifications first if needed, otherwise go straight to
            // call/contacts; the effect below picks up call/contacts once
            // the notification dialog has been resolved.
            if (notificationPermissionState?.status?.isGranted == false) {
                notificationPermissionState.launchPermissionRequest()
            } else if (!callPermissionsState.allPermissionsGranted) {
                callPermissionsState.launchMultiplePermissionRequest()
            }
        }
    }

    if (notificationPermissionState != null) {
        LaunchedEffect(notificationPermissionState.status) {
            if (permissionsState.allPermissionsGranted && !callPermissionsState.allPermissionsGranted) {
                callPermissionsState.launchMultiplePermissionRequest()
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

        LocalAiStatusLabel(
            status = uiState.localAiStatus,
            onDownload = viewModel::downloadLocalAiModel
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

/**
 * Honesty over silence: AICore/Gemini Nano is only present on a handful of
 * devices today (confirmed missing on a plain emulator — "AiCoreService: not
 * found"). Where it's absent, RoadMate downloads a small open model on its
 * own (Wi-Fi only) and this label shows the progress, so the user isn't left
 * guessing why answers are generic. Nothing about a question ever leaves the
 * phone except weather — the model download is a one-time plain HTTPS fetch
 * of an openly-licensed file, no account, no query data.
 */
@Composable
private fun LocalAiStatusLabel(
    status: LocalAiStatus,
    onDownload: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        when (status) {
            LocalAiStatus.ReadyAicore, LocalAiStatus.ReadyLocalModel ->
                StatusText("IA local activa", scheme.tertiary)

            LocalAiStatus.Checking ->
                StatusText("Comprobando IA local...", scheme.onSurfaceVariant)

            LocalAiStatus.ModelDownloadable ->
                StatusText("Modo básico · preparando descarga de IA local...", scheme.onSurfaceVariant)

            is LocalAiStatus.Downloading -> {
                StatusText(
                    "Descargando IA local... ${(status.progress * 100).toInt()} %",
                    scheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { status.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(220.dp)
                )
            }

            LocalAiStatus.WaitingForWifi ->
                StatusText("IA local · se descargará al conectar a Wi-Fi", scheme.onSurfaceVariant)

            is LocalAiStatus.DownloadFailed -> {
                StatusText("No se pudo descargar la IA local", scheme.error)
                TextButton(onClick = onDownload) { Text("Reintentar") }
            }

            LocalAiStatus.Unavailable ->
                StatusText("Modo básico · sin IA local en este dispositivo", scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
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
    val haptics = LocalHapticFeedback.current
    // A small "pop" plus a confirm tick when the answer actually lands — the
    // driver may not be looking at the screen, so the arrival needs to be
    // felt, not just read.
    val cardScale = remember { Animatable(1f) }
    LaunchedEffect(uiState.status) {
        if (uiState.status == RoadMateStatus.SPEAKING) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            cardScale.snapTo(0.96f)
            cardScale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

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
                .scale(cardScale.value)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    // Announce to TalkBack when the answer arrives — the driver
                    // may be looking at the road, not the screen, when it does.
                    .semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                val listening = uiState.status == RoadMateStatus.LISTENING
                if (uiState.lastRecognizedInput.isNotBlank()) {
                    // Updates live from partial recognition results — the user
                    // sees the words landing as they speak.
                    Text(
                        text = "Tú: “${uiState.lastRecognizedInput}”",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                val bodyText = when {
                    uiState.currentResponse.isNotBlank() -> uiState.currentResponse
                    listening && uiState.lastRecognizedInput.isBlank() -> "Escuchando..."
                    listening -> ""
                    else -> "Pulsa el micrófono y haz tu pregunta."
                }
                if (bodyText.isNotEmpty()) {
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (uiState.currentResponse.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
            }
        }

        MicButton(isListening = uiState.isListening, onClick = onMicClick)
    }
}

@Composable
private fun MicButton(isListening: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    // Short built-in tones instead of a bundled audio asset — no extra
    // resource to ship, and TONE_PROP_BEEP/BEEP2 are designed for exactly
    // this kind of "start/stop" UI cue. STREAM_NOTIFICATION so it honors
    // the device's notification volume/silent mode automatically.
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }
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
                onClick = {
                    haptics.performHapticFeedback(
                        if (isListening) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                    )
                    toneGenerator?.startTone(
                        if (isListening) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP,
                        120
                    )
                    onClick()
                },
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
 * real amplitude readout. Vosk exposes no RMS callback, so mic-reactive bar
 * heights would need a separate AudioRecord tap — not worth it for a flourish.
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
