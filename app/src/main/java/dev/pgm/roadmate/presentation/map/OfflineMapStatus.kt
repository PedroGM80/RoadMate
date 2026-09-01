package dev.pgm.roadmate.presentation.map

/**
 * State of the downloaded-for-offline map area(s), surfaced on [MapScreen].
 */
sealed interface OfflineMapStatus {

    /** Not yet checked. */
    data object Unknown : OfflineMapStatus

    /** Checked — nothing saved for offline use. */
    data object Idle : OfflineMapStatus

    /** A region download is running. [progress] is 0f‑1f, best effort. */
    data class Downloading(val progress: Float) : OfflineMapStatus

    /** At least one area is saved and usable without a connection. */
    data class Ready(val regionCount: Int) : OfflineMapStatus

    /** The download failed; [message] is user-facing Spanish text. */
    data class Failed(val message: String) : OfflineMapStatus
}

/**
 * Pure mapping of a region-download progress tick to [OfflineMapStatus] —
 * extracted so it can be unit-tested without a MapLibre `OfflineRegionStatus`.
 */
fun offlineMapProgress(
    completedResources: Long,
    requiredResources: Long,
    isComplete: Boolean,
): OfflineMapStatus =
    if (isComplete) {
        OfflineMapStatus.Ready(regionCount = -1)
    } else {
        val total = requiredResources.coerceAtLeast(1L)
        val fraction = (completedResources.toDouble() / total).toFloat()
        OfflineMapStatus.Downloading(fraction.coerceIn(0f, 0.99f))
    }
