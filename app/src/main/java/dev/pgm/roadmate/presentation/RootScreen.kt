package dev.pgm.roadmate.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.pgm.roadmate.R
import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.LocalAiModel
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.presentation.map.MapScreen
import dev.pgm.roadmate.presentation.map.MapViewModel
import dev.pgm.roadmate.presentation.map.rememberMapPaneState
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

@Composable
private fun SettingsMenu(
    theme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    answerStyle: AnswerStyle,
    onAnswerStyleChange: (AnswerStyle) -> Unit,
    handsFree: Boolean,
    onHandsFreeChange: (Boolean) -> Unit,
    localAiModels: List<LocalAiModel>,
    localAiStatus: LocalAiStatus,
    selectedLocalAiModelId: String,
    onSelectLocalAiModel: (String) -> Unit,
    onRetryLocalAi: () -> Unit,
    onClearOfflineMaps: () -> Unit,
    onClearMemory: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingModel by remember { mutableStateOf<LocalAiModel?>(null) }
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
        MenuHeader(R.string.settings_voice_header)
        DropdownMenuItem(
            leadingIcon = { Switch(checked = handsFree, onCheckedChange = null) },
            text = { Text(stringResource(R.string.settings_hands_free)) },
            onClick = { onHandsFreeChange(!handsFree) },
        )

        HorizontalDivider()
        MenuHeader(R.string.settings_local_ai_header)
        Text(
            localAiStatusText(localAiStatus),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        localAiModels.forEach { model ->
            DropdownMenuItem(
                leadingIcon = { RadioButton(selected = model.id == selectedLocalAiModelId, onClick = null) },
                text = {
                    Column {
                        Text(
                            buildString {
                                append(model.name)
                                if (model.recommended) append("  ·  ${stringResource(R.string.settings_local_ai_recommended)}")
                                if (model.approxSize.isNotEmpty()) append("  ·  ${model.approxSize}")
                            },
                        )
                        Text(
                            model.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    if (model.id != selectedLocalAiModelId) pendingModel = model
                },
            )
        }
        if (localAiStatus is LocalAiStatus.DownloadFailed) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.action_retry),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { close(); onRetryLocalAi() },
            )
        }

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

    pendingModel?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingModel = null },
            title = { Text(stringResource(R.string.settings_local_ai_switch_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_local_ai_switch_body,
                        if (model.approxSize.isEmpty()) model.name else "${model.name} (${model.approxSize})",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectLocalAiModel(model.id)
                    pendingModel = null
                    close()
                }) { Text(stringResource(R.string.settings_local_ai_switch_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingModel = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Compact one-liner for the settings menu's "IA local" status row. */
@Composable
private fun localAiStatusText(status: LocalAiStatus): String = when (status) {
    LocalAiStatus.ReadyAicore -> stringResource(R.string.settings_local_ai_nano)
    LocalAiStatus.ReadyLocalModel -> stringResource(R.string.ai_ready)
    LocalAiStatus.Checking -> stringResource(R.string.ai_checking)
    LocalAiStatus.ModelDownloadable -> stringResource(R.string.ai_preparing_download)
    is LocalAiStatus.Downloading ->
        stringResource(R.string.ai_downloading, (status.progress * 100).toInt())
    LocalAiStatus.WaitingForWifi -> stringResource(R.string.ai_waiting_wifi)
    is LocalAiStatus.DownloadFailed -> stringResource(R.string.ai_download_failed)
    LocalAiStatus.Unavailable -> stringResource(R.string.ai_unavailable)
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
