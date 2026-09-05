package dev.pgm.roadmate.domain.model

/**
 * State of the offline routing data (BRouter `.rd5` segment tiles) for the
 * area a route needs. Surfaced on the map while a tile downloads.
 */
sealed interface RoutingDataStatus {

    /** Nothing in flight — tiles for the current area are present or untried. */
    data object Idle : RoutingDataStatus

    /** A segment tile is downloading. [progress] is 0f–1f, best effort. */
    data class Downloading(val progress: Float) : RoutingDataStatus

    /** The tiles a route asked for are on disk. */
    data object Ready : RoutingDataStatus

    /**
     * Download is held until Wi-Fi is available, because the driver turned
     * off "descargar con datos móviles". Only ever reached by choice — the
     * default allows mobile data, since a car on the road has no Wi-Fi and
     * gating on it made routing fail exactly where it is needed.
     */
    data object WaitingForWifi : RoutingDataStatus

    /** No usable connection at all, so a missing tile can't be fetched. */
    data object NoNetwork : RoutingDataStatus

    /** The tile download failed; [message] is a short human-readable reason. */
    data class Failed(val message: String) : RoutingDataStatus
}
