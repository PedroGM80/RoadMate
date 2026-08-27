package dev.pgm.roadmate.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.pgm.roadmate.domain.repository.OnboardingRepository
import dev.pgm.roadmate.presentation.screen.HomeScreen
import dev.pgm.roadmate.presentation.screen.OnboardingScreen
import dev.pgm.roadmate.presentation.viewmodel.RoadMateViewModel
import dev.pgm.roadmate.service.SilenceDetectionForegroundService
import dev.pgm.roadmate.ui.theme.RoadMateTheme
import dev.pgm.roadmate.utils.PermissionManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: RoadMateViewModel by viewModels()

    @Inject
    lateinit var permissionManager: PermissionManager

    @Inject
    lateinit var onboardingRepository: OnboardingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoadMateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val isOnboardingCompleted by onboardingRepository.isOnboardingCompleted
                        .collectAsState(initial = null)

                    when (isOnboardingCompleted) {
                        null -> Unit // still reading DataStore — render nothing rather than flash a screen
                        false -> OnboardingScreen(
                            onContinue = { lifecycleScope.launch { onboardingRepository.setOnboardingCompleted() } },
                            modifier = Modifier.padding(innerPadding)
                        )
                        true -> HomeScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SilenceDetectionForegroundService.stop(this)
        if (permissionManager.hasRecordAudioPermission()) {
            viewModel.startSilenceMonitoring()
        }
    }

    override fun onPause() {
        super.onPause()
        // Don't leave the mic recording once the app isn't in the foreground.
        viewModel.cancelListening()

        // Hand rest-reminder monitoring off to the foreground service instead of
        // just dropping it — only one of the two should hold the mic at a time.
        viewModel.stopSilenceMonitoring()
        if (permissionManager.hasRecordAudioPermission()) {
            SilenceDetectionForegroundService.start(this)
        }
    }
}
