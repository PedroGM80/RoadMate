package dev.pgm.roadmate.presentation.screen

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dev.pgm.roadmate.presentation.viewmodel.RoadMateStatus
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

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.startSilenceMonitoring()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.LocationOn, contentDescription = null)
            Text(
                text = uiState.location
                    ?.let { "%.4f, %.4f".format(it.first, it.second) }
                    ?: "Buscando ubicación...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Text(
            text = statusLabel(uiState.status),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Text(
            text = uiState.currentResponse.ifBlank { "Pulsa el micrófono y haz tu pregunta." },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (permissionsState.allPermissionsGranted) {
            Button(
                onClick = {
                    if (uiState.isListening) viewModel.cancelListening() else viewModel.startListening()
                },
                modifier = Modifier.size(width = 200.dp, height = 64.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null
                )
                Text(
                    text = if (uiState.isListening) "Hablar" else "Escuchar",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        } else {
            val rationale = if (permissionsState.shouldShowRationale) {
                "RoadMate necesita el micrófono y la ubicación para responder tus preguntas."
            } else {
                "Concede permisos de micrófono y ubicación para empezar."
            }
            Text(text = rationale, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Conceder permisos")
            }
        }
    }
}

private fun statusLabel(status: RoadMateStatus): String = when (status) {
    RoadMateStatus.IDLE -> ""
    RoadMateStatus.LISTENING -> "Escuchando..."
    RoadMateStatus.PROCESSING -> "Procesando..."
    RoadMateStatus.SPEAKING -> "Reproduciendo..."
}
