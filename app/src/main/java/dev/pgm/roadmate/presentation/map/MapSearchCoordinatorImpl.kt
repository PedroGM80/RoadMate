package dev.pgm.roadmate.presentation.map

import dev.pgm.roadmate.domain.model.MapSearchRequest
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process implementation: a small buffered [MutableSharedFlow] the voice
 * pipeline writes to and [RootScreen] / [MapViewModel] read from.
 * [hasOfflineMap] reads the live [OfflineMapController] status so the voice
 * side can decide whether an offline answer is even possible before it
 * speaks.
 */
@Singleton
class MapSearchCoordinatorImpl @Inject constructor(
    private val offlineMap: OfflineMapController,
) : MapSearchCoordinator {

    private val _requests = MutableSharedFlow<MapSearchRequest>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    override val requests: Flow<MapSearchRequest> = _requests.asSharedFlow()

    override fun hasOfflineMap(): Boolean =
        offlineMap.status.value is OfflineMapStatus.Ready

    override suspend fun submit(request: MapSearchRequest) {
        _requests.emit(request)
    }
}
