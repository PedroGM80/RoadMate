package dev.pgm.roadmate.utils

import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.ContactMatch
import dev.pgm.roadmate.domain.model.PhoneLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These cover the one place RoadMate can do something irreversible to a third
 * party: `Found` means "dial now, no confirmation". Every test here is really
 * asking the same question — is RoadMate allowed to decide this on its own?
 */
class ContactMatchingTest {

    private fun contact(name: String, number: String = "600000000", label: PhoneLabel = PhoneLabel.OTHER) =
        ContactMatch(name, number, label)

    @Test
    fun `nothing matched is not a call`() {
        assertEquals(ContactLookupResult.NotFound, ContactMatching.resolve(emptyList(), "ana"))
    }

    @Test
    fun `one match is dialled`() {
        val ana = contact("Ana García")
        assertEquals(ContactLookupResult.Found(ana), ContactMatching.resolve(listOf(ana), "Ana García"))
    }

    @Test
    fun `an exact name beats a longer one that merely contains it`() {
        val ana = contact("Ana", "600000001")
        val juana = contact("Juana", "600000002")

        // The provider returns both; only "Ana" is what was said.
        val result = ContactMatching.resolve(listOf(juana, ana), "Ana")

        assertEquals(ContactLookupResult.Found(ana), result)
    }

    @Test
    fun `a surname matches by word, not by accident`() {
        val ana = contact("Ana García Ruiz")
        assertEquals(ContactMatching.Tier.WORD_PREFIX, ContactMatching.tierOf("Ana García Ruiz", "García"))
        assertEquals(ContactLookupResult.Found(ana), ContactMatching.resolve(listOf(ana), "García"))
    }

    @Test
    fun `accents and case don't matter`() {
        assertEquals(ContactMatching.Tier.EXACT, ContactMatching.tierOf("Ana García", "ana garcia"))
        assertEquals(ContactMatching.Tier.EXACT, ContactMatching.tierOf("JOSÉ", "josé"))
    }

    @Test
    fun `two different people in the best tier is a question, never a guess`() {
        val one = contact("Ana Ruiz", "600000001")
        val two = contact("Ana Soler", "600000002")

        val result = ContactMatching.resolve(listOf(one, two), "Ana")

        assertTrue(result is ContactLookupResult.Ambiguous)
        assertEquals(listOf(one, two), (result as ContactLookupResult.Ambiguous).matches)
    }

    @Test
    fun `a weak match is never mixed in with a strong one`() {
        val exact = contact("Ana", "600000001")
        val loose = contact("Mariana", "600000002")

        val result = ContactMatching.resolve(listOf(exact, loose), "Ana")

        // "Mariana" also contains "ana" — but it must not turn a certain call
        // into a question, nor be offered as an alternative.
        assertEquals(ContactLookupResult.Found(exact), result)
    }

    @Test
    fun `one person on two nameable numbers is asked about`() {
        val mobile = contact("Ana", "600000001", PhoneLabel.MOBILE)
        val work = contact("Ana", "910000001", PhoneLabel.WORK)

        val result = ContactMatching.resolve(listOf(mobile, work), "Ana")

        assertTrue(result is ContactLookupResult.Ambiguous)
    }

    @Test
    fun `one person on two numbers we can't tell apart is just dialled`() {
        val a = contact("Ana", "600000001", PhoneLabel.OTHER)
        val b = contact("Ana", "600000002", PhoneLabel.OTHER)

        // Asking "¿el otro o el otro?" helps nobody.
        assertEquals(ContactLookupResult.Found(a), ContactMatching.resolve(listOf(a, b), "Ana"))
    }

    @Test
    fun `a number the contact named themselves can be offered and picked`() {
        val mobile = contact("Ana", "600000001", PhoneLabel.MOBILE)
        val car = ContactMatch("Ana", "600000002", PhoneLabel.OTHER, customLabel = "Coche")

        // Before, "Coche" came back as an unnameable "otro", so RoadMate
        // couldn't offer the choice and just dialled the first number.
        val result = ContactMatching.resolve(listOf(mobile, car), "Ana")
        assertTrue(result is ContactLookupResult.Ambiguous)
        assertEquals("el de coche", car.spokenLabel)

        assertEquals(car, CallFollowUpParser.resolve("el coche", listOf(mobile, car)))
        assertEquals(mobile, CallFollowUpParser.resolve("el móvil", listOf(mobile, car)))
    }

    @Test
    fun `the same line stored twice is one number`() {
        assertEquals(
            ContactMatching.normalizeNumber("+34 600 11 22 33"),
            ContactMatching.normalizeNumber("+34600112233"),
        )
        assertEquals("600112233", ContactMatching.normalizeNumber("600-11-22-33"))
    }

    @Test
    fun `a garbled transcript can't turn into a call to a stranger`() {
        // "%" used to be pasted into a LIKE pattern, where it matched every
        // contact in the phone and the first row was then dialled. Nothing
        // answers to it by name any more, so several candidates stay
        // several candidates — a question, not a call.
        assertEquals(ContactMatching.Tier.SUBSTRING, ContactMatching.tierOf("Ana", "%"))

        val everyone = listOf(contact("Ana", "1"), contact("Bruno", "2"), contact("Carla", "3"))
        assertTrue(ContactMatching.resolve(everyone, "%") is ContactLookupResult.Ambiguous)
    }
}
