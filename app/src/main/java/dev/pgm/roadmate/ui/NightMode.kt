package dev.pgm.roadmate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.pgm.roadmate.domain.model.ThemePreference
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

/**
 * Whether RoadMate should render dark right now, for [preference].
 *
 * The point of the loop is [ThemePreference.AUTO]. It used to be evaluated
 * once, at composition time, so a drive that started in daylight stayed in the
 * light theme straight through dusk — exactly the moment the setting exists
 * for, and exactly when a bright screen in a car windscreen is worst. Now the
 * night check is re-run on a timer while AUTO is selected, and not at all
 * otherwise.
 *
 * Five minutes, not one: the flip is imperceptibly late at that granularity
 * and it is 288 idle wake-ups a day instead of 1440. Computing the exact next
 * sunrise/sunset would be more precise, but it needs its own handling for the
 * polar cases [dev.pgm.roadmate.utils.SolarClock] already reasons about, and
 * buys nothing anyone can see.
 */
@Composable
fun rememberIsDarkTheme(
    preference: ThemePreference,
    location: Pair<Double, Double>?,
): Boolean {
    val systemDark = isSystemInDarkTheme()
    var autoNight by remember { mutableStateOf(ThemePreference.isNight(ZonedDateTime.now(), location)) }

    LaunchedEffect(preference, location) {
        if (preference != ThemePreference.AUTO) return@LaunchedEffect
        while (true) {
            autoNight = ThemePreference.isNight(ZonedDateTime.now(), location)
            delay(RECHECK_INTERVAL_MS.milliseconds)
        }
    }

    return when (preference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.AUTO -> autoNight
    }
}

private const val RECHECK_INTERVAL_MS = 5 * 60 * 1000L
