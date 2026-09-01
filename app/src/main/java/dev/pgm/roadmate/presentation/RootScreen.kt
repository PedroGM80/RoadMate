package dev.pgm.roadmate.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.pgm.roadmate.presentation.map.MapScreen
import dev.pgm.roadmate.presentation.map.MapViewModel
import dev.pgm.roadmate.presentation.screen.HomeScreen
import dev.pgm.roadmate.presentation.viewmodel.RoadMateViewModel

/**
 * Two-tab shell: the voice assistant ("Voz") and the in-app offline map
 * ("Mapa"). No navigation library — a single saved tab index, in keeping
 * with how lean the rest of the app's navigation is.
 */
@Composable
fun RootScreen(
    roadMateViewModel: RoadMateViewModel,
    mapViewModel: MapViewModel,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                    label = { Text("Voz") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                    label = { Text("Mapa") },
                )
            }
        },
    ) { innerPadding ->
        when (tab) {
            0 -> HomeScreen(viewModel = roadMateViewModel, modifier = Modifier.padding(innerPadding))
            else -> MapScreen(viewModel = mapViewModel, modifier = Modifier.padding(innerPadding))
        }
    }
}
