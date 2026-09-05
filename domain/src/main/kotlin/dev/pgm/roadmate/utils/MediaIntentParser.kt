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
    private val TRIGGER = spanishRegex(
        """\b(?:abre|abrir|pon|poner|ponme|reproduce|reproducir|inicia|iniciar|lanza|lanzar|arranca|arrancar)\b""",
    )

    private val APP_PATTERNS: List<Pair<Regex, MediaApp>> = listOf(
        spanishRegex("""\b(?:youtube\s*music|yt\s*music)\b""") to MediaApp.YOUTUBE_MUSIC,
        spanishRegex("""\bspotify\b""") to MediaApp.SPOTIFY,
    )

    /**
     * "pon música", "pon algo de música", "quiero música" — a request for
     * music that never names an app.
     *
     * This is how people actually ask, and it used to fall through to the
     * model, which would answer *about* music instead of playing any. There
     * is no app to name here, so the caller picks: see
     * [dev.pgm.roadmate.domain.repository.MediaRepository.launchAnyMusicApp].
     */
    private val MUSIC_WITHOUT_APP = spanishRegex(
        """(?:^|\s)(?:quiero\s+|ponme\s+|pon\s+|poner\s+|reproduce\s+|escuchar\s+)""" +
            """(?:algo\s+de\s+|un\s+poco\s+de\s+|algo\s+)?""" +
            """(?:m[uú]sica|canciones|una\s+canci[oó]n)\b""",
    )

    fun extractMediaApp(userInput: String): MediaApp? {
        val text = userInput.trim()
        if (!TRIGGER.containsMatchIn(text)) return null
        // YouTube Music is matched before the bare "spotify" pattern so
        // "youtube music" can't be shadowed by an unrelated later rule.
        return APP_PATTERNS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second
    }

    /** True for a music request that named no app at all. */
    fun wantsMusicWithoutApp(userInput: String): Boolean {
        val text = userInput.trim()
        return extractMediaApp(text) == null && MUSIC_WITHOUT_APP.containsMatchIn(text)
    }
}
