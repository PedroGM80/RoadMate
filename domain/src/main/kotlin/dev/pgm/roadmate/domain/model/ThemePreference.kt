package dev.pgm.roadmate.domain.model

/** How RoadMate picks light vs dark. Persisted, applied app-wide. */
enum class ThemePreference {
    /** Follow the system setting. */
    SYSTEM,
    LIGHT,
    DARK,

    /** Dark from dusk to dawn regardless of the system setting. */
    AUTO,
    ;

    companion object {
        val DEFAULT = SYSTEM

        /** [hour] 0–23. Dusk-to-dawn window used by [AUTO]. */
        fun isNightHour(hour: Int): Boolean = hour < 7 || hour >= 20
    }
}
