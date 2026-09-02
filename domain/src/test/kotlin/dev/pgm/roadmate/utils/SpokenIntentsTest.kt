package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phrasings a driver actually uses, as opposed to the ones the parsers
 * were written against. Everything here fell through to the model before —
 * including three things the README promises out loud.
 */
class SpokenIntentsTest {

    // ---- answer length: the README advertises "con más detalle" ------------

    @Test
    fun `style can be set without saying the word respuestas`() {
        assertEquals(AnswerStyle.DETAILED, StylePreferenceParser.parse("con más detalle"))
        assertEquals(AnswerStyle.DETAILED, StylePreferenceParser.parse("más detalle"))
        assertEquals(AnswerStyle.BRIEF, StylePreferenceParser.parse("sé más breve"))
        assertEquals(AnswerStyle.BRIEF, StylePreferenceParser.parse("más corto"))
        assertEquals(AnswerStyle.BRIEF, StylePreferenceParser.parse("responde más conciso"))
        assertEquals(AnswerStyle.DETAILED, StylePreferenceParser.parse("contéstame más largo"))
    }

    @Test
    fun `the explicit form still works`() {
        assertEquals(AnswerStyle.BRIEF, StylePreferenceParser.parse("respuestas cortas"))
        assertEquals(AnswerStyle.NORMAL, StylePreferenceParser.parse("respuestas normales"))
    }

    @Test
    fun `asking for short content is not asking for short answers`() {
        // The whole reason the original pattern was anchored on "respuestas".
        assertNull(StylePreferenceParser.parse("cuéntame algo corto de este pueblo"))
        assertNull(StylePreferenceParser.parse("dime un chiste más corto que el anterior"))
    }

    // ---- music without naming an app --------------------------------------

    @Test
    fun `pon musica is a music request even with no app named`() {
        assertTrue(MediaIntentParser.wantsMusicWithoutApp("pon música"))
        assertTrue(MediaIntentParser.wantsMusicWithoutApp("pon algo de música"))
        assertTrue(MediaIntentParser.wantsMusicWithoutApp("ponme música"))
        assertTrue(MediaIntentParser.wantsMusicWithoutApp("quiero música"))
    }

    @Test
    fun `naming an app still picks that app, not just any`() {
        assertFalse(MediaIntentParser.wantsMusicWithoutApp("pon música en spotify"))
        assertEquals(
            dev.pgm.roadmate.domain.model.MediaApp.SPOTIFY,
            MediaIntentParser.extractMediaApp("pon música en spotify"),
        )
    }

    @Test
    fun `talking about music is not asking for it`() {
        assertFalse(MediaIntentParser.wantsMusicWithoutApp("qué música le gusta a la gente aquí"))
        assertFalse(MediaIntentParser.wantsMusicWithoutApp("cuánto queda"))
    }

    // ---- a need is a search ------------------------------------------------

    @Test
    fun `a stated need finds the right kind of place`() {
        val cases = mapOf(
            "tengo hambre" to PlaceCategory.FOOD,
            "quiero comer algo" to PlaceCategory.FOOD,
            "necesito echar gasolina" to PlaceCategory.FUEL,
            "me estoy quedando sin gasolina" to PlaceCategory.FUEL,
            "hay que repostar" to PlaceCategory.FUEL,
            "necesito parar a descansar" to PlaceCategory.HOTEL,
            "quiero dormir" to PlaceCategory.HOTEL,
        )
        for ((said, expected) in cases) {
            val query = MapSearchIntentParser.extractSearchQuery(said)
            assertEquals("«$said» should be a place search", expected, query?.let(PlaceCategoryParser::parse))
        }
    }

    @Test
    fun `a question about fuel is still a question`() {
        // "gasolina" appears here too, and this one belongs to the model.
        assertNull(MapSearchIntentParser.extractSearchQuery("cuánta gasolina me queda"))
        assertNull(MapSearchIntentParser.extractSearchQuery("cuánto cuesta la gasolina"))
    }

    @Test
    fun `an explicit search still wins over an inferred need`() {
        assertEquals("un hotel", MapSearchIntentParser.extractSearchQuery("busca un hotel"))
    }

    // ---- more ways to ask for a call --------------------------------------

    @Test
    fun `the other ways people ask for a call`() {
        assertEquals("ana", CallIntentParser.extractContactName("llama a ana"))
        assertEquals("ana", CallIntentParser.extractContactName("marca el número de ana"))
        assertEquals("ana", CallIntentParser.extractContactName("ponme con ana"))
        assertEquals("ana", CallIntentParser.extractContactName("pásame con ana"))
        assertEquals("mamá", CallIntentParser.extractContactName("quiero hablar con mamá"))
        assertEquals("josé maría", CallIntentParser.extractContactName("llámame a josé maría"))
        assertEquals("mi hermano", CallIntentParser.extractContactName("llama a mi hermano"))
    }

    @Test
    fun `asking for music is never read as a call`() {
        assertNull(CallIntentParser.extractContactName("ponme música"))
        assertNull(CallIntentParser.extractContactName("pon algo de música"))
    }
}
