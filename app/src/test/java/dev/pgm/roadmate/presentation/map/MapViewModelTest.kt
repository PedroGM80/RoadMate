package dev.pgm.roadmate.presentation.map

import dev.pgm.roadmate.presentation.viewmodel.fake.FakeMemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.geometry.LatLngBounds

private class FakeOfflineMap : OfflineMapController {
    val statusFlow = MutableStateFlow<OfflineMapStatus>(OfflineMapStatus.Unknown)
    override val status: StateFlow<OfflineMapStatus> = statusFlow
    var refreshCount = 0
    var downloads = 0

    override fun refresh() { refreshCount++ }
    override fun download(styleUrl: String, bounds: LatLngBounds, pixelRatio: Float) { downloads++ }
}

private fun mapViewModel(offlineMap: OfflineMapController) =
    MapViewModel(offlineMap, FakeMemoryRepository())

class MapViewModelTest {

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
}
