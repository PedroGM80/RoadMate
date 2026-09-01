package dev.pgm.roadmate.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.window.core.layout.WindowSizeClass
import dev.pgm.roadmate.R
import dev.pgm.roadmate.presentation.map.MapScreen
import dev.pgm.roadmate.presentation.map.MapViewModel
import dev.pgm.roadmate.presentation.screen.HomeScreen
import dev.pgm.roadmate.presentation.viewmodel.RoadMateViewModel

/**
 * Two destinations: the voice assistant ("Voz") and the offline map ("Mapa").
 *
 * On a phone-width window they're tabs behind a bottom bar (a rail once
 * there's room for one — [NavigationSuiteScaffold] makes that switch on its
 * own). From ~840dp wide (tablet, unfolded foldable, desktop) both show at
 * once, side by side, and the nav chrome disappears since there's nothing
 * left to switch between. Still no navigation library — one saved [tab] int.
 */
@Composable
fun RootScreen(
    roadMateViewModel: RoadMateViewModel,
    mapViewModel: MapViewModel,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // The 1-arg overload is deprecated in favour of a V2 that adds L/XL width
    // classes; the EXPANDED lower bound already covers those, and V2 isn't in
    // adaptive 1.3.x. Revisit when that dependency bumps.
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
                label = { Text("Voz") },
            )
            item(
                selected = tab == 1,
                onClick = { tab = 1 },
                icon = { Icon(painterResource(R.drawable.lucide_ic_map), contentDescription = null) },
                label = { Text("Mapa") },
            )
        },
    ) {
        // Inner Scaffold only for its window-inset padding — HomeScreen and
        // MapScreen don't inset themselves, they relied on the shell for it.
        Scaffold { innerPadding ->
            val paneModifier = Modifier.padding(innerPadding)
            if (dualPane) {
                Row(paneModifier.fillMaxSize()) {
                    HomeScreen(
                        viewModel = roadMateViewModel,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    VerticalDivider()
                    MapScreen(
                        viewModel = mapViewModel,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                when (tab) {
                    0 -> HomeScreen(viewModel = roadMateViewModel, modifier = paneModifier.fillMaxSize())
                    else -> MapScreen(viewModel = mapViewModel, modifier = paneModifier.fillMaxSize())
                }
            }
        }
    }
}
