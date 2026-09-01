package dev.pgm.roadmate.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.presentation.map.OfflineMapController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the shell's overflow menu: theme choice and "forget everything". */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AssistantPreferencesRepository,
    private val memory: MemoryRepository,
    private val offlineMap: OfflineMapController,
    location: LocationRepository,
) : ViewModel() {

    val theme: StateFlow<ThemePreference> = preferences.themePreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.DEFAULT)

    /** Last known position, for the AUTO theme's sunrise/sunset check. Null until a fix lands. */
    val lastLocation: StateFlow<Pair<Double, Double>?> = location.location

    val answerStyle: StateFlow<AnswerStyle> = preferences.answerStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, AnswerStyle.DEFAULT)

    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch { preferences.setThemePreference(preference) }
    }

    fun setAnswerStyle(style: AnswerStyle) {
        viewModelScope.launch { preferences.setAnswerStyle(style) }
    }

    fun clearMemory() {
        viewModelScope.launch { memory.clearAll() }
    }

    fun clearOfflineMaps() = offlineMap.deleteAll()
}
