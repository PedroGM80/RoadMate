package dev.pgm.roadmate.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.domain.repository.OnboardingRepository
import dev.pgm.roadmate.presentation.map.MapViewModel
import dev.pgm.roadmate.presentation.screen.OnboardingScreen
import dev.pgm.roadmate.presentation.viewmodel.RoadMateViewModel
import dev.pgm.roadmate.presentation.viewmodel.SettingsViewModel
import dev.pgm.roadmate.service.SilenceDetectionForegroundService
import dev.pgm.roadmate.ui.theme.RoadMateTheme
import dev.pgm.roadmate.utils.PermissionManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: RoadMateViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var permissionManager: PermissionManager

    @Inject
    lateinit var onboardingRepository: OnboardingRepository

    /** Set by the Quick Settings tile; consumed in onResume once the UI is up. */
    private var pendingStartListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingStartListening = intent.getBooleanExtra(EXTRA_START_LISTENING, false)
        enableEdgeToEdge()
        setContent {
            val themePreference by settingsViewModel.theme.collectAsState()
            val location by settingsViewModel.lastLocation.collectAsState()
            val dark = when (themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                ThemePreference.AUTO -> ThemePreference.isNight(java.time.ZonedDateTime.now(), location)
            }
            RoadMateTheme(darkTheme = dark) {
                val isOnboardingCompleted by onboardingRepository.isOnboardingCompleted
                    .collectAsState(initial = null)

                when (isOnboardingCompleted) {
                    null -> Unit // still reading DataStore — render nothing rather than flash a screen
                    false -> OnboardingScreen(
                        onContinue = { lifecycleScope.launch { onboardingRepository.setOnboardingCompleted() } },
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars)
                    )
                    true -> RootScreen(
                        roadMateViewModel = viewModel,
                        mapViewModel = mapViewModel,
                        settingsViewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingStartListening = intent.getBooleanExtra(EXTRA_START_LISTENING, false)
    }

    override fun onResume() {
        super.onResume()
        SilenceDetectionForegroundService.stop(this)
        if (permissionManager.hasRecordAudioPermission()) {
            // Picks the wake-word listener or the silence monitor — one, not both.
            viewModel.startAmbientListening()
            if (pendingStartListening) {
                pendingStartListening = false
                viewModel.startListening()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Clear the caller's intent before cancelling the mic capture, so the
        // capture's teardown doesn't revive wake-word listening we're stopping.
        viewModel.stopAmbientListening()
        // Don't leave the mic recording once the app isn't in the foreground.
        viewModel.cancelListening()

        // Hand rest-reminder monitoring off to the foreground service instead of
        // just dropping it — only one of the two should hold the mic at a time.
        if (permissionManager.hasRecordAudioPermission()) {
            SilenceDetectionForegroundService.start(this)
        }
    }

    companion object {
        /** Intent extra: open straight into listening (from the QS tile). */
        const val EXTRA_START_LISTENING = "dev.pgm.roadmate.START_LISTENING"
    }
}
