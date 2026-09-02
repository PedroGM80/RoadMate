package dev.pgm.roadmate.presentation.map

import dev.pgm.roadmate.domain.repository.CurrentPlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-process holder: [MapScreen] resolves the label from the tiles and
 *  writes it here; [dev.pgm.roadmate.presentation.viewmodel.RoadMateViewModel]
 *  reads it for the location chip. */
@Singleton
class CurrentPlaceRepositoryImpl @Inject constructor() : CurrentPlaceRepository {
    private val _label = MutableStateFlow<String?>(null)
    override val label: StateFlow<String?> = _label.asStateFlow()
    override fun update(label: String?) {
        _label.value = label?.trim()?.takeIf { it.isNotEmpty() }
    }
}
