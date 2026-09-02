package dev.pgm.roadmate.presentation.map

import dev.pgm.roadmate.domain.model.MapSearchRequest
import dev.pgm.roadmate.domain.model.PlaceCategory
import dev.pgm.roadmate.presentation.viewmodel.MainDispatcherRule
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeMapSearchCoordinator
import dev.pgm.roadmate.presentation.viewmodel.fake.FakeMemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.geometry.LatLngBounds

private class FakeOfflineMap : OfflineMapController {
    val statusFlow = MutableStateFlow<OfflineMapStatus>(OfflineMapStatus.Unknown)
    override val status: StateFlow<OfflineMapStatus> = statusFlow
    var refreshCount = 0
    var downloads = 0

    var deletes = 0
    override fun refresh() { refreshCount++ }
    override fun download(styleUrl: String, bounds: LatLngBounds, pixelRatio: Float) { downloads++ }
    override fun deleteAll() { deletes++ }
}

private fun mapViewModel(
    offlineMap: OfflineMapController,
    coordinator: FakeMapSearchCoordinator = FakeMapSearchCoordinator(),
) = MapViewModel(offlineMap, FakeMemoryRepository(), coordinator)

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `refreshes offline regions on construction`() {
        val fake = FakeOfflineMap()
        mapViewModel(fake)
        assertEquals(1, fake.refreshCount)
    }

    @Test
    fun `toggling a POI filter selects it, then clears it`() {
        val vm = mapViewModel(FakeOfflineMap())
        assertNull(vm.poiFilter.value)

        vm.togglePoiFilter(PoiKind.FUEL)
        assertEquals(PoiKind.FUEL, vm.poiFilter.value)

        vm.togglePoiFilter(PoiKind.HOTEL)
        assertEquals(PoiKind.HOTEL, vm.poiFilter.value)

        vm.togglePoiFilter(PoiKind.HOTEL)
        assertNull(vm.poiFilter.value)
    }

    @Test
    fun `offline status is passed straight through from the controller`() {
        val fake = FakeOfflineMap()
        val vm = mapViewModel(fake)

        fake.statusFlow.value = OfflineMapStatus.Downloading(0.4f)
        assertEquals(OfflineMapStatus.Downloading(0.4f), vm.offlineStatus.value)
    }

    @Test
    fun `downloadVisibleRegion delegates to the controller`() {
        val fake = FakeOfflineMap()
        val vm = mapViewModel(fake)

        vm.downloadVisibleRegion(LatLngBounds.from(1.0, 1.0, 0.0, 0.0), pixelRatio = 2f)

        assertEquals(1, fake.downloads)
    }

    @Test
    fun `a category voice search sets the POI filter and signals the shell`() = runTest {
        val coordinator = FakeMapSearchCoordinator()
        val vm = mapViewModel(FakeOfflineMap(), coordinator)
        val shown = mutableListOf<Unit>()
        val job = launch { vm.showMap.toList(shown) }
        advanceUntilIdle()

        coordinator.submit(MapSearchRequest("una gasolinera", PlaceCategory.FUEL, 40.0 to -3.7))
        advanceUntilIdle()

        assertEquals(PoiKind.FUEL, vm.poiFilter.value)
        assertNull(vm.nameQuery.value)
        assertEquals(1, shown.size)
        job.cancel()
    }

    @Test
    fun `a name voice search sets the name query, not a POI filter`() = runTest {
        val coordinator = FakeMapSearchCoordinator()
        val vm = mapViewModel(FakeOfflineMap(), coordinator)

        coordinator.submit(MapSearchRequest("el Mercadona", category = null, origin = null))
        advanceUntilIdle()

        assertNull(vm.poiFilter.value)
        assertEquals("el Mercadona", vm.nameQuery.value)
    }
}
