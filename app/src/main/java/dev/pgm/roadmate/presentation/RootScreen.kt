package dev.pgm.roadmate.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.pgm.roadmate.R
import dev.pgm.roadmate.presentation.map.MapScreen
import dev.pgm.roadmate.presentation.map.MapViewModel
import dev.pgm.roadmate.presentation.map.rememberMapPaneState
import dev.pgm.roadmate.presentation.screen.HomeScreen
import dev.pgm.roadmate.presentation.viewmodel.RoadMateViewModel
import dev.pgm.roadmate.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    roadMateViewModel: RoadMateViewModel,
    mapViewModel: MapViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Held here, above the Crossfade: MapScreen leaves composition on every
    // tab switch, and a MapView owned by it was destroyed and rebuilt each
    // time (GL context torn down, style re-fetched). Now the switch only
    // detaches and re-attaches the view.
    val mapPane = rememberMapPaneState()
    val theme by settingsViewModel.theme.collectAsStateWithLifecycle()
    val answerStyle by settingsViewModel.answerStyle.collectAsStateWithLifecycle()
    val handsFree by settingsViewModel.handsFree.collectAsStateWithLifecycle()
    val localAiStatus by settingsViewModel.localAiStatus.collectAsStateWithLifecycle()
    val selectedLocalAiModelId by settingsViewModel.selectedLocalAiModelId.collectAsStateWithLifecycle()

    // A voice search ("busca gasolineras") pulls the map to the front.
    LaunchedEffect(Unit) {
        mapViewModel.showMap.collect { tab = 1 }
    }

    @Suppress("DEPRECATION")
    val dualPane = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    NavigationSuiteScaffold(
        modifier = modifier.fillMaxSize(),
        navigationSuiteItems = {
            if (dualPane) return@NavigationSuiteScaffold
            item(
                selected = tab == 0,
                onClick = { tab = 0 },
                icon = { Icon(painterResource(R.drawable.lucide_ic_mic), contentDescription = null) },
                label = { Text(stringResource(R.string.tab_voice)) },
            )
            item(
                selected = tab == 1,
                onClick = { tab = 1 },
                icon = { Icon(painterResource(R.drawable.lucide_ic_map), contentDescription = null) },
                label = { Text(stringResource(R.string.tab_map)) },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        SettingsMenu(
                            theme = theme,
                            onThemeChange = settingsViewModel::setTheme,
                            answerStyle = answerStyle,
                            onAnswerStyleChange = settingsViewModel::setAnswerStyle,
                            handsFree = handsFree,
                            onHandsFreeChange = settingsViewModel::setHandsFree,
                            localAiModels = settingsViewModel.localAiModels,
                            localAiStatus = localAiStatus,
                            selectedLocalAiModelId = selectedLocalAiModelId,
                            onSelectLocalAiModel = settingsViewModel::selectLocalAiModel,
                            onRetryLocalAi = settingsViewModel::retryLocalAiDownload,
                            onClearOfflineMaps = settingsViewModel::clearOfflineMaps,
                            onClearMemory = settingsViewModel::clearMemory,
                        )
                    },
                )
            },
        ) { innerPadding ->
            val paneModifier = Modifier.padding(innerPadding)
            if (dualPane) {
                Row(paneModifier.fillMaxSize()) {
                    HomeScreen(roadMateViewModel, Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider()
                    MapScreen(mapViewModel, Modifier.weight(1f).fillMaxHeight(), mapPane)
                }
            } else {
                Crossfade(targetState = tab, label = "voz-mapa") { current ->
                    when (current) {
                        0 -> HomeScreen(roadMateViewModel, paneModifier.fillMaxSize())
                        else -> MapScreen(mapViewModel, paneModifier.fillMaxSize(), mapPane)
                    }
                }
            }
        }
    }
}
