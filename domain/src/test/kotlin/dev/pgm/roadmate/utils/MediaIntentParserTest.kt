package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.MediaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaIntentParserTest {

    @Test
    fun `recognizes Spotify across common phrasings and casing`() {
        assertEquals(MediaApp.SPOTIFY, MediaIntentParser.extractMediaApp("abre Spotify"))
        assertEquals(MediaApp.SPOTIFY, MediaIntentParser.extractMediaApp("pon música en spotify"))
        assertEquals(MediaApp.SPOTIFY, MediaIntentParser.extractMediaApp("REPRODUCE ALGO EN SPOTIFY"))
        assertEquals(MediaApp.SPOTIFY, MediaIntentParser.extractMediaApp("ponme Spotify"))
    }

    @Test
    fun `recognizes YouTube Music and is not shadowed by the bare music word`() {
        assertEquals(MediaApp.YOUTUBE_MUSIC, MediaIntentParser.extractMediaApp("abre YouTube Music"))
        assertEquals(MediaApp.YOUTUBE_MUSIC, MediaIntentParser.extractMediaApp("pon yt music"))
    }

    @Test
    fun `needs both a launch verb and an app name`() {
        // App named but no verb — not a command.
        assertNull(MediaIntentParser.extractMediaApp("¿tienes Spotify instalado?"))
        // Verb but no app RoadMate knows.
        assertNull(MediaIntentParser.extractMediaApp("pon música"))
        assertNull(MediaIntentParser.extractMediaApp("abre la ventana"))
    }

    @Test
    fun `does not misfire on unrelated questions`() {
        assertNull(MediaIntentParser.extractMediaApp("¿cuánto queda para llegar?"))
        assertNull(MediaIntentParser.extractMediaApp("busca una gasolinera"))
    }
}
