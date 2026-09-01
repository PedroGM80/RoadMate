package dev.pgm.roadmate.presentation.screen

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import dev.pgm.roadmate.R
import dev.pgm.roadmate.presentation.components.AnswerCard
import dev.pgm.roadmate.presentation.components.InfoPill
import dev.pgm.roadmate.presentation.components.LocalAiStatusLabel
import dev.pgm.roadmate.presentation.components.MicButton
import dev.pgm.roadmate.presentation.components.PermissionRequest
import dev.pgm.roadmate.presentation.viewmodel.RoadMateStatus
import dev.pgm.roadmate.presentation.viewmodel.RoadMateUiState
import dev.pgm.roadmate.presentation.viewmodel.RoadMateViewModel
import dev.pgm.roadmate.ui.rememberReduceMotion
import dev.pgm.roadmate.ui.theme.Spacing

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: RoadMateViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val reduceMotion = rememberReduceMotion()

    val corePermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION),
    )
    // Notifications only gate the background silence-monitor notification, not
    // the voice loop; call/contacts only affect "llama a X" (which explains
    // itself when denied). Android allows one permission dialog at a time, so
    // these are chained: core → notifications → call/contacts.
    val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }
    val callPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS),
    )

    LaunchedEffect(corePermissions.allPermissionsGranted) {
        if (corePermissions.allPermissionsGranted) {
            viewModel.startSilenceMonitoring()
            viewModel.refreshLocation()
            viewModel.greetIfNeeded()
            if (notificationPermission?.status?.isGranted == false) {
                notificationPermission.launchPermissionRequest()
            } else if (!callPermissions.allPermissionsGranted) {
                callPermissions.launchMultiplePermissionRequest()
            }
        }
    }
    if (notificationPermission != null) {
        LaunchedEffect(notificationPermission.status) {
            if (corePermissions.allPermissionsGranted && !callPermissions.allPermissionsGranted) {
                callPermissions.launchMultiplePermissionRequest()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        LocalAiStatusLabel(
            status = uiState.localAiStatus,
            onRetry = viewModel::downloadLocalAiModel,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        LocationRow(
            location = uiState.location,
            unavailable = uiState.locationUnavailable,
            onRetry = viewModel::refreshLocation,
            modifier = Modifier.padding(top = Spacing.sm),
        )

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (corePermissions.allPermissionsGranted) {
                GrantedContent(
                    uiState = uiState,
                    reduceMotion = reduceMotion,
                    onMicClick = {
                        if (uiState.isListening) viewModel.cancelListening() else viewModel.startListening()
                    },
                )
            } else {
                PermissionRequest(
                    shouldShowRationale = corePermissions.shouldShowRationale,
                    onRequestClick = corePermissions::launchMultiplePermissionRequest,
                )
            }
        }
    }
}

@Composable
private fun LocationRow(
    location: Pair<Double, Double>?,
    unavailable: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when {
        location != null -> stringResource(R.string.location_coords, location.first, location.second)
        unavailable -> stringResource(R.string.location_unavailable)
        else -> stringResource(R.string.location_searching)
    }
    InfoPill(
        label = label,
        icon = painterResource(R.drawable.lucide_ic_map_pin),
        modifier = modifier,
        onClick = if (unavailable) onRetry else null,
    )
}

@Composable
private fun GrantedContent(
    uiState: RoadMateUiState,
    reduceMotion: Boolean,
    onMicClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (uiState.status != RoadMateStatus.IDLE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = Spacing.sm + Spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                if (uiState.status == RoadMateStatus.PROCESSING) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp).padding(end = Spacing.sm),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = statusLabel(uiState.status),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        AnswerCard(
            uiState = uiState,
            reduceMotion = reduceMotion,
            modifier = Modifier.padding(bottom = Spacing.xxl),
        )

        MicButton(
            isListening = uiState.isListening,
            onClick = onMicClick,
            reduceMotion = reduceMotion,
        )
    }
}

@Composable
private fun statusLabel(status: RoadMateStatus): String = when (status) {
    RoadMateStatus.IDLE -> ""
    RoadMateStatus.LISTENING -> stringResource(R.string.home_state_listening)
    RoadMateStatus.PROCESSING -> stringResource(R.string.home_state_processing)
    RoadMateStatus.SPEAKING -> stringResource(R.string.home_state_speaking)
}
