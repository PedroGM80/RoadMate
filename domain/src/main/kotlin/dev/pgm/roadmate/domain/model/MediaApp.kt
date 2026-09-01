package dev.pgm.roadmate.domain.model

/**
 * A music app RoadMate knows how to launch by voice. [packageName] is what
 * the data layer resolves a launch intent against; [displayName] is spoken
 * back to the driver.
 */
enum class MediaApp(val packageName: String, val displayName: String) {
    SPOTIFY("com.spotify.music", "Spotify"),
    YOUTUBE_MUSIC("com.google.android.apps.youtube.music", "YouTube Music"),
}
