package dev.pgm.roadmate.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceTest {

    @Test
    fun `night is before 7 and from 20 onwards`() {
        assertTrue(ThemePreference.isNightHour(0))
        assertTrue(ThemePreference.isNightHour(6))
        assertFalse(ThemePreference.isNightHour(7))
        assertFalse(ThemePreference.isNightHour(13))
        assertFalse(ThemePreference.isNightHour(19))
        assertTrue(ThemePreference.isNightHour(20))
        assertTrue(ThemePreference.isNightHour(23))
    }
}
