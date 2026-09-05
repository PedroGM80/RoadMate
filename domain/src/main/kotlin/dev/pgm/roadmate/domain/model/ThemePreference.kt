package dev.pgm.roadmate.domain.model

import dev.pgm.roadmate.utils.SolarClock
import java.time.ZonedDateTime

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

        /** [hour] 0–23. Fixed dusk-to-dawn window — the fallback for [AUTO] when
         *  there's no location fix to compute real sunrise/sunset from. */
        fun isNightHour(hour: Int): Boolean = hour !in 7..<20

        /**
         * Whether [AUTO] should be dark right now: real sunrise/sunset at
         * [location] when it's known, otherwise the fixed [isNightHour] window.
         */
        fun isNight(now: ZonedDateTime, location: Pair<Double, Double>?): Boolean =
            if (location == null) isNightHour(now.hour)
            else SolarClock.isNight(now, location.first, location.second)
    }
}
