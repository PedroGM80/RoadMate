package dev.pgm.roadmate.domain.model

/** How RoadMate picks light vs dark. Persisted, applied app-wide. */
enum class ThemePreference {
    /** Follow the system setting. */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        val DEFAULT = SYSTEM
    }
}
