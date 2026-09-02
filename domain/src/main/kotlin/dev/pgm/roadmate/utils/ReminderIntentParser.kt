package dev.pgm.roadmate.utils

/**
 * "recuérdame llamar al taller en media hora", "recuérdame que compre pan a
 * las 6", "avísame en 20 minutos de la reunión". Returns the reminder text
 * plus *either* a relative delay or an absolute clock time — the use case
 * turns that into an epoch millis using the current time.
 */
object ReminderIntentParser {

    data class Reminder(
        val text: String,
        val delayMinutes: Int? = null,
        val atHour: Int? = null,
        val atMinute: Int = 0,
    )

    private val LEAD = spanishRegex(
        """^(?:recu[eé]rdame|recordarme|av[ií]same|acu[eé]rdame)\s+(?:de\s+|que\s+)?""",
    )

    // "… en 20 minutos" / "… dentro de media hora" / "… en hora y media"
    private val IN_TAIL = spanishRegex(
        """\s+(?:en|dentro\s+de|de\s+aqu[ií]\s+a)\s+(.+)$""",
    )
    // "… a las seis" / "… a las 6 y media" / "… a las 6 y cuarto" / "… a las 6 menos cuarto"
    private val AT_TAIL = spanishRegex(
        """\s+a\s+la[s]?\s+([\p{L}\d]+)(?::(\d{1,2}))?(?:\s+y\s+(media|cuarto)|\s+menos\s+cuarto)?\s*$""",
    )

    fun parse(userInput: String): Reminder? {
        val stripped = userInput.trim().trim('¿', '?', '¡', '!', '.', ' ')
        if (!LEAD.containsMatchIn(stripped)) return null
        val body = LEAD.replaceFirst(stripped, "").trim()
        if (body.isBlank()) return null

        AT_TAIL.find(body)?.let { m ->
            val text = body.removeRange(m.range).trim().ifBlank { return null }
            val hour = SpanishNumbers.parse(m.groupValues[1])?.toInt()
                ?.let { if (it in 0..23) it else return null } ?: return null
            var minute = m.groupValues[2].toIntOrNull() ?: 0
            var h = hour
            when {
                m.groupValues[3] == "media" -> minute = 30
                m.groupValues[3] == "cuarto" -> minute = 15
                m.value.contains("menos cuarto") -> { minute = 45; h = (hour + 23) % 24 }
            }
            return Reminder(text = text, atHour = h, atMinute = minute)
        }

        IN_TAIL.find(body)?.let { m ->
            val text = body.removeRange(m.range).trim().ifBlank { return null }
            val minutes = durationMinutes(m.groupValues[1].trim()) ?: return null
            return Reminder(text = text, delayMinutes = minutes)
        }

        return null
    }

    private fun durationMinutes(raw: String): Int? {
        val t = raw.lowercase().trim()
        when (t) {
            "media hora", "una media hora" -> return 30
            "un cuarto de hora", "cuarto de hora" -> return 15
            "hora y media", "una hora y media" -> return 90
            "un rato" -> return 10
        }
        val m = spanishRegex("""^(.+?)\s*(minutos?|min|horas?|h)$""").find(t) ?: return null
        val n = SpanishNumbers.parse(m.groupValues[1].trim()) ?: return null
        val unit = m.groupValues[2]
        return if (unit.startsWith("h")) (n * 60).toInt() else n.toInt()
    }
}
