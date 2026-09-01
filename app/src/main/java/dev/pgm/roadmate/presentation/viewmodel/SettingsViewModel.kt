package dev.pgm.roadmate.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.MemoryRepository
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
) : ViewModel() {

    val theme: StateFlow<ThemePreference> = preferences.themePreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.DEFAULT)

    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch { preferences.setThemePreference(preference) }
    }

    fun clearMemory() {
        viewModelScope.launch { memory.clearAll() }
    }
}
