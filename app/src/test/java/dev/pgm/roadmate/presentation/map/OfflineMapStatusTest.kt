package dev.pgm.roadmate.presentation.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapStatusTest {

    @Test
    fun `in-progress tick maps to a clamped download fraction`() {
        val status = offlineMapProgress(completedResources = 25, requiredResources = 100, isComplete = false)
        assertEquals(OfflineMapStatus.Downloading(0.25f), status)
    }

    @Test
    fun `fraction never reaches 1 until isComplete`() {
        val status = offlineMapProgress(completedResources = 100, requiredResources = 100, isComplete = false)
        val downloading = status as OfflineMapStatus.Downloading
        assertTrue(downloading.progress < 1f)
    }

    @Test
    fun `zero required resources does not divide by zero`() {
        val status = offlineMapProgress(completedResources = 0, requiredResources = 0, isComplete = false)
        assertEquals(OfflineMapStatus.Downloading(0f), status)
    }

    @Test
    fun `isComplete maps to Ready regardless of counts`() {
        assertEquals(
            OfflineMapStatus.Ready(regionCount = -1),
            offlineMapProgress(completedResources = 3, requiredResources = 999, isComplete = true),
        )
    }
}
