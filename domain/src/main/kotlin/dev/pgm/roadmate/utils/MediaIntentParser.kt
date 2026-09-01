package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.MediaApp

/**
 * Recognizes "abre Spotify", "pon música en YouTube Music", "reproduce algo
 * en Spotify" and similar — a request to open a named music app. Pure text
 * matching, no Android dependency; checked before Gemini in
 * GenerateResponseUseCase, same shortcut pattern as [CallIntentParser].
 *
 * Only launches the app — it does not start playback (that needs a media
 * session the driver's app doesn't expose to third parties). The spoken
 * reply says "abriendo", not "reproduciendo", so it stays honest.
 */
object MediaIntentParser {

    /** Verbs that, followed somewhere by an app name, mean "open that app". */
    private val TRIGGER = Regex(
        """\b(?:abre|abrir|pon|poner|ponme|reproduce|reproducir|inicia|iniciar|lanza|lanzar|arranca|arrancar)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val APP_PATTERNS: List<Pair<Regex, MediaApp>> = listOf(
        Regex("""\b(?:youtube\s*music|yt\s*music)\b""", RegexOption.IGNORE_CASE) to MediaApp.YOUTUBE_MUSIC,
        Regex("""\bspotify\b""", RegexOption.IGNORE_CASE) to MediaApp.SPOTIFY,
    )

    fun extractMediaApp(userInput: String): MediaApp? {
        val text = userInput.trim()
        if (!TRIGGER.containsMatchIn(text)) return null
        // YouTube Music is matched before the bare "spotify" pattern so
        // "youtube music" can't be shadowed by an unrelated later rule.
        return APP_PATTERNS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second
    }
}
