package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceChunkerTest {

    @Test
    fun `emits a sentence once whitespace proves it is complete`() {
        val chunker = SentenceChunker()

        // Terminal punctuation at the very end is not enough — could be a
        // decimal or an ellipsis mid-token.
        assertEquals(emptyList<String>(), chunker.consume("Para en el área de servicio."))
        // The following space confirms the sentence ended.
        assertEquals(
            listOf("Para en el área de servicio."),
            chunker.consume("Para en el área de servicio. Hay"),
        )
    }

    @Test
    fun `several sentences in one chunk all come out in order`() {
        val chunker = SentenceChunker()

        val out = chunker.consume("Gira a la derecha ahora. Sigue recto un kilómetro. Luego ")

        assertEquals(
            listOf("Gira a la derecha ahora.", "Sigue recto un kilómetro."),
            out,
        )
    }

    @Test
    fun `the trailing fragment is returned by flush`() {
        val chunker = SentenceChunker()
        chunker.consume("Ya casi llegas. Un par de curvas más")

        assertEquals("Un par de curvas más", chunker.flush())
    }

    @Test
    fun `question and exclamation marks are boundaries too`() {
        val chunker = SentenceChunker()

        assertEquals(
            listOf("¿Quieres que busque una gasolinera?"),
            chunker.consume("¿Quieres que busque una gasolinera? Hay"),
        )
    }

    @Test
    fun `decimals do not split a sentence`() {
        val chunker = SentenceChunker()

        assertEquals(
            emptyList<String>(),
            chunker.consume("Quedan 12.5 kilómetros para el destino y "),
        )
        assertEquals("Quedan 12.5 kilómetros para el destino y", chunker.flush())
    }

    @Test
    fun `a short list marker is not spoken on its own`() {
        val chunker = SentenceChunker()

        // "1." then space — under the minimum visible-character count.
        assertEquals(emptyList<String>(), chunker.consume("1. Gira a la izquierda"))
    }

    @Test
    fun `a closing quote after the mark stays with the sentence`() {
        val chunker = SentenceChunker()

        assertEquals(
            listOf("Te ha dicho \"llego tarde\"."),
            chunker.consume("Te ha dicho \"llego tarde\". Deberías"),
        )
    }

    @Test
    fun `token-by-token streaming yields each sentence exactly once`() {
        val chunker = SentenceChunker()
        val full = "Gira a la derecha. Sigue recto medio kilómetro. Has llegado."
        val spoken = mutableListOf<String>()

        val sb = StringBuilder()
        for (word in full.split(" ")) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(word)
            spoken += chunker.consume(sb.toString())
        }
        chunker.flush()?.let { spoken += it }

        assertEquals(
            listOf(
                "Gira a la derecha.",
                "Sigue recto medio kilómetro.",
                "Has llegado.",
            ),
            spoken,
        )
    }

    @Test
    fun `a mutating trailing character does not drop earlier text`() {
        val chunker = SentenceChunker()

        // An emission that ends mid-word with a replacement char (the mojibake
        // repair upstream hasn't seen the next byte yet)...
        assertEquals(emptyList<String>(), chunker.consume("Quedan tres kil�"))
        // ...is resolved by the next emission, which also completes the sentence.
        assertEquals(
            listOf("Quedan tres kilómetros."),
            chunker.consume("Quedan tres kilómetros. Sigue"),
        )
    }

    @Test
    fun `flush resets so the chunker can be reused`() {
        val chunker = SentenceChunker()
        chunker.consume("Primera respuesta sin punto")
        assertEquals("Primera respuesta sin punto", chunker.flush())

        assertEquals(
            listOf("Segunda respuesta completa."),
            chunker.consume("Segunda respuesta completa. Y"),
        )
    }
}
