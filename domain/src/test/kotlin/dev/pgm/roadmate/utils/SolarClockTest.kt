package dev.pgm.roadmate.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SolarClockTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val madridLat = 40.4168
    private val madridLon = -3.7038

    private fun madrid(date: LocalDate, time: LocalTime): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(date, time), madrid)

    @Test
    fun `midday is day and small hours are night, midsummer`() {
        val solstice = LocalDate.of(2026, 6, 21)
        assertTrue(SolarClock.isNight(madrid(solstice, LocalTime.of(4, 0)), madridLat, madridLon))
        assertFalse(SolarClock.isNight(madrid(solstice, LocalTime.of(14, 0)), madridLat, madridLon))
        assertTrue(SolarClock.isNight(madrid(solstice, LocalTime.of(23, 30)), madridLat, madridLon))
    }

    @Test
    fun `winter dusk comes earlier than the fixed 8pm window`() {
        val solstice = LocalDate.of(2026, 12, 21)
        // Madrid sunset in late December is ~17:55 — dark by 19:00, which the
        // old fixed isNightHour() window (dark only from 20:00) would miss.
        assertFalse(SolarClock.isNight(madrid(solstice, LocalTime.of(12, 0)), madridLat, madridLon))
        assertTrue(SolarClock.isNight(madrid(solstice, LocalTime.of(19, 0)), madridLat, madridLon))
        assertTrue(SolarClock.isNight(madrid(solstice, LocalTime.of(7, 30)), madridLat, madridLon))
    }

    @Test
    fun `polar night stays dark around the clock`() {
        val tromso = ZoneId.of("Europe/Oslo")
        val noon = ZonedDateTime.of(LocalDateTime.of(2026, 12, 21, 12, 0), tromso)
        assertTrue(SolarClock.isNight(noon, 69.6492, 18.9553))
    }

    @Test
    fun `midnight sun stays light around the clock`() {
        val tromso = ZoneId.of("Europe/Oslo")
        val midnight = ZonedDateTime.of(LocalDateTime.of(2026, 6, 21, 0, 30), tromso)
        assertFalse(SolarClock.isNight(midnight, 69.6492, 18.9553))
    }
}
