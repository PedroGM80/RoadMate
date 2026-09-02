package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.MediaApp

/**
 * Opens a music app on the device. The query itself never leaves the phone —
 * this just fires a launch intent at an app the driver already has installed.
 */
interface MediaRepository {

    /** Launches [app]. Returns false if it isn't installed or can't be started. */
    fun launchMediaApp(app: MediaApp): Boolean

    /**
     * Opens whichever music app the driver actually has, for a request that
     * named none ("pon música"). Returns the app opened, or null if none of
     * the ones RoadMate knows about is installed — so the spoken reply can
     * name what it opened instead of guessing.
     */
    fun launchAnyMusicApp(): MediaApp?
}
