package dev.pgm.roadmate.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Sunrise / sunset from a date and a position, so the AUTO theme can flip on
 * real dusk instead of a fixed 20:00–07:00 guess. Pure Kotlin (this module has
 * no Android deps): the standard low-precision sunrise equation, good to about
 * a minute — plenty for "is it dark out".
 *
 * https://en.wikipedia.org/wiki/Sunrise_equation
 */
object SolarClock {

    /** Sun's centre 0.833° below the horizon = sunrise/sunset (refraction + disc radius). */
    private const val HORIZON_DEG = -0.833
    private const val OBLIQUITY_DEG = 23.4397
    /** Epoch day (days since 1970-01-01) of 2000-01-01, the equation's origin. */
    private const val J2000_EPOCH_DAY = 10957L
    /** Julian date of the Unix epoch. */
    private const val UNIX_EPOCH_JULIAN = 2440587.5

    /**
     * True when [now] is before sunrise or at/after sunset for its own date and
     * zone at the given coordinates. At high latitudes where the sun never
     * crosses the horizon, returns true through the polar night and false
     * through the midnight-sun period.
     */
    fun isNight(now: ZonedDateTime, latitude: Double, longitude: Double): Boolean {
        val s = solar(now.toLocalDate(), longitude)
        val latRad = Math.toRadians(latitude)
        val cosHourAngle =
            (sin(Math.toRadians(HORIZON_DEG)) - sin(latRad) * s.sinDeclination) /
                (cos(latRad) * s.cosDeclination)

        if (cosHourAngle > 1.0) return true   // sun never rises — polar night
        if (cosHourAngle < -1.0) return false // sun never sets — midnight sun

        val hourAngleFraction = Math.toDegrees(acos(cosHourAngle)) / 360.0
        val sunrise = julianToZoned(s.julianTransit - hourAngleFraction, now)
        val sunset = julianToZoned(s.julianTransit + hourAngleFraction, now)
        return now.isBefore(sunrise) || !now.isBefore(sunset)
    }

    private class Solar(val julianTransit: Double, val sinDeclination: Double, val cosDeclination: Double)

    private fun solar(date: LocalDate, longitude: Double): Solar {
        val n = (date.toEpochDay() - J2000_EPOCH_DAY).toDouble()
        val meanSolarNoon = n - longitude / 360.0
        val meanAnomaly = Math.toRadians((357.5291 + 0.98560028 * meanSolarNoon).mod(360.0))
        val center =
            1.9148 * sin(meanAnomaly) + 0.0200 * sin(2 * meanAnomaly) + 0.0003 * sin(3 * meanAnomaly)
        val eclipticLongitude =
            Math.toRadians((Math.toDegrees(meanAnomaly) + center + 282.9372).mod(360.0))
        val julianTransit = 2451545.0 + meanSolarNoon +
            0.0053 * sin(meanAnomaly) - 0.0069 * sin(2 * eclipticLongitude)
        val sinDeclination = sin(eclipticLongitude) * sin(Math.toRadians(OBLIQUITY_DEG))
        return Solar(julianTransit, sinDeclination, cos(asin(sinDeclination)))
    }

    private fun julianToZoned(julian: Double, reference: ZonedDateTime): ZonedDateTime {
        val epochMillis = ((julian - UNIX_EPOCH_JULIAN) * 86_400_000.0).roundToLong()
        return Instant.ofEpochMilli(epochMillis).atZone(reference.zone)
    }
}
