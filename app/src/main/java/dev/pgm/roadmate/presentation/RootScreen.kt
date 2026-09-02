package dev.pgm.roadmate.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import dev.pgm.roadmate.R
import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.presentation.map.MapScreen
import dev.pgm.roadmate.presentation.map.MapViewModel
import dev.pgm.roadmate.presentation.screen.HomeScreen
import dev.pgm.roadmate.presentation.viewmodel.RoadMateViewModel
import dev.pgm.roadmate.presentation.viewmodel.SettingsViewModel

/**
 * Two destinations: the voice assistant ("Voz") and the offline map ("Mapa"),
 * under one [TopAppBar] that carries the settings overflow (theme, clear
 * memory). Phone width = tabs behind a bottom bar; from ~840dp both panes
 * show side by side and the nav chrome drops. One saved [tab] int, no nav
 * library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    roadMateViewModel: RoadMateViewModel,
    mapViewModel: MapViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val theme by settingsViewModel.theme.collectAsState()
    val answerStyle by settingsViewModel.answerStyle.collectAsState()

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
                    MapScreen(mapViewModel, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Crossfade(targetState = tab, label = "voz-mapa") { current ->
                    when (current) {
                        0 -> HomeScreen(roadMateViewModel, paneModifier.fillMaxSize())
                        else -> MapScreen(mapViewModel, paneModifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenu(
    theme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    answerStyle: AnswerStyle,
    onAnswerStyleChange: (AnswerStyle) -> Unit,
    onClearOfflineMaps: () -> Unit,
    onClearMemory: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val close = { open = false }

    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.lucide_ic_settings), contentDescription = stringResource(R.string.settings))
    }
    DropdownMenu(expanded = open, onDismissRequest = close) {
        MenuHeader(R.string.settings_theme_header)
        RadioItem(ThemePreference.SYSTEM, R.string.theme_system, theme, onThemeChange, close)
        RadioItem(ThemePreference.LIGHT, R.string.theme_light, theme, onThemeChange, close)
        RadioItem(ThemePreference.DARK, R.string.theme_dark, theme, onThemeChange, close)
        RadioItem(ThemePreference.AUTO, R.string.theme_auto, theme, onThemeChange, close)

        HorizontalDivider()
        MenuHeader(R.string.settings_answers_header)
        RadioItem(AnswerStyle.BRIEF, R.string.answer_brief, answerStyle, onAnswerStyleChange, close)
        RadioItem(AnswerStyle.NORMAL, R.string.answer_normal, answerStyle, onAnswerStyleChange, close)
        RadioItem(AnswerStyle.DETAILED, R.string.answer_detailed, answerStyle, onAnswerStyleChange, close)

        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.clear_offline_maps)) },
            onClick = { close(); onClearOfflineMaps() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.clear_memory), color = MaterialTheme.colorScheme.error) },
            onClick = { open = false; confirmClear = true },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_memory_confirm_title)) },
            text = { Text(stringResource(R.string.clear_memory_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearMemory() }) {
                    Text(stringResource(R.string.clear_memory_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun MenuHeader(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun <T> RadioItem(
    value: T,
    labelRes: Int,
    current: T,
    onChange: (T) -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = { RadioButton(selected = current == value, onClick = null) },
        text = { Text(stringResource(labelRes)) },
        onClick = { onChange(value); close() },
    )
}
