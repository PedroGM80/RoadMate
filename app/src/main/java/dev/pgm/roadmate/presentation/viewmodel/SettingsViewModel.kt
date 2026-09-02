package dev.pgm.roadmate.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.LocalAiCatalog
import dev.pgm.roadmate.domain.model.LocalAiModel
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.presentation.map.OfflineMapController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the shell's overflow menu: theme, answer length, voice, local AI, "forget everything". */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AssistantPreferencesRepository,
    private val memory: MemoryRepository,
    private val offlineMap: OfflineMapController,
    private val gemini: GeminiRepository,
    location: LocationRepository,
) : ViewModel() {

    /** Models the driver can pick for the downloadable local-AI backend. */
    val localAiModels: List<LocalAiModel> = gemini.localAiModels

    val localAiStatus: StateFlow<LocalAiStatus> = gemini.localAiStatus()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalAiStatus.Checking)

    val selectedLocalAiModelId: StateFlow<String> = gemini.selectedLocalAiModelId()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalAiCatalog.recommended.id)

    fun selectLocalAiModel(id: String) {
        viewModelScope.launch { gemini.selectLocalAiModel(id) }
    }

    fun retryLocalAiDownload() {
        viewModelScope.launch { gemini.requestLocalAiModelDownload() }
    }

    val theme: StateFlow<ThemePreference> = preferences.themePreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.DEFAULT)

    /** Last known position, for the AUTO theme's sunrise/sunset check. Null until a fix lands. */
    val lastLocation: StateFlow<Pair<Double, Double>?> = location.location

    val answerStyle: StateFlow<AnswerStyle> = preferences.answerStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, AnswerStyle.DEFAULT)

    val handsFree: StateFlow<Boolean> = preferences.handsFreeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch { preferences.setThemePreference(preference) }
    }

    fun setAnswerStyle(style: AnswerStyle) {
        viewModelScope.launch { preferences.setAnswerStyle(style) }
    }

    fun setHandsFree(enabled: Boolean) {
        viewModelScope.launch { preferences.setHandsFreeEnabled(enabled) }
    }

    fun clearMemory() {
        viewModelScope.launch { memory.clearAll() }
    }

    fun clearOfflineMaps() = offlineMap.deleteAll()
}
