package dev.pgm.roadmate.utils

/**
 * Turns a growing model response into speakable sentences.
 *
 * Streaming backends hand the answer back a few tokens at a time. Speaking
 * every token would be gibberish and speaking only once the whole reply is
 * ready throws away the latency win, so this batches the stream to sentence
 * boundaries: feed it the cumulative text with [consume] and it returns
 * whichever sentences have completed since the previous call. Call [flush]
 * once the stream ends to get the trailing fragment — while it is still the
 * newest text, the last sentence usually has no terminal mark yet.
 *
 * A boundary is `.`, `!`, `?` or `…`, optionally followed by a closing
 * quote/bracket, with whitespace after it — so a cut only happens once we can
 * see that more text follows — and with at least [minChars] visible
 * characters in the sentence, which keeps list markers ("1.") and decimals
 * ("12.5") from being spoken on their own.
 *
 * Each call re-scans the whole cumulative string (a few hundred chars, cheap)
 * rather than appending deltas, so it stays correct when a trailing character
 * mutates between emissions — the mojibake repair upstream can turn a partial
 * "kil<27>" into "kiló…" once the next byte arrives.
 */
class SentenceChunker(private val minChars: Int = MIN_SENTENCE_CHARS) {

    /** Chars of the cumulative text already returned as sentences. */
    private var spokenPrefix = 0
    private var lastText = ""

    /** New complete sentences since the last call; may be empty. */
    fun consume(cumulativeText: String): List<String> {
        lastText = cumulativeText
        if (cumulativeText.length < spokenPrefix) spokenPrefix = 0 // shrank / restarted
        if (cumulativeText.length == spokenPrefix) return emptyList()

        val sentences = mutableListOf<String>()
        var start = spokenPrefix
        var i = spokenPrefix
        while (i < cumulativeText.length) {
            if (cumulativeText[i] in TERMINALS) {
                var j = i + 1
                while (j < cumulativeText.length && cumulativeText[j] in CLOSERS) j++
                if (j < cumulativeText.length && cumulativeText[j].isWhitespace()) {
                    val sentence = cumulativeText.substring(start, j).trim()
                    if (sentence.count { !it.isWhitespace() } >= minChars) {
                        sentences += sentence
                        start = j
                        i = j
                        continue
                    }
                }
            }
            i++
        }
        spokenPrefix = start
        return sentences
    }

    /** The leftover text (a final sentence with no terminal mark), or null. */
    fun flush(): String? {
        val rest = if (lastText.length >= spokenPrefix) {
            lastText.substring(spokenPrefix).trim()
        } else {
            lastText.trim()
        }
        spokenPrefix = 0
        lastText = ""
        return rest.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val MIN_SENTENCE_CHARS = 12
        val TERMINALS = charArrayOf('.', '!', '?', '…').toHashSet()
        val CLOSERS = charArrayOf('"', '\'', ')', ']', '»', '”', '’').toHashSet()
    }
}
