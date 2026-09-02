package dev.pgm.roadmate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderIntentParserTest {

    @Test
    fun `relative-delay reminders`() {
        ReminderIntentParser.parse("recuérdame llamar al taller en media hora")!!.let {
            assertEquals("llamar al taller", it.text)
            assertEquals(30, it.delayMinutes)
        }
        ReminderIntentParser.parse("recuérdame que compre pan en 20 minutos")!!.let {
            assertEquals("compre pan", it.text)
            assertEquals(20, it.delayMinutes)
        }
        ReminderIntentParser.parse("avísame de la reunión dentro de dos horas")!!.let {
            assertEquals("la reunión", it.text)
            assertEquals(120, it.delayMinutes)
        }
    }

    @Test
    fun `clock-time reminders`() {
        ReminderIntentParser.parse("recuérdame sacar la basura a las ocho")!!.let {
            assertEquals("sacar la basura", it.text)
            assertEquals(8, it.atHour)
            assertEquals(0, it.atMinute)
        }
        ReminderIntentParser.parse("recuérdame la cita a las seis y media")!!.let {
            assertEquals(6, it.atHour)
            assertEquals(30, it.atMinute)
        }
    }

    @Test
    fun `not a reminder`() {
        listOf(
            "recuérdame lo que te dije",
            "¿cuánto queda?",
            "pon música",
            "llama a Ana",
        ).forEach { assertNull(it, ReminderIntentParser.parse(it)) }
    }
}
