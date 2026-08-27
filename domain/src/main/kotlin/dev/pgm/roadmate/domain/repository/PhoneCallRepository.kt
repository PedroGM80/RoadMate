package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.ContactLookupResult

/**
 * Contract for the "llama a X" voice command: look a contact up by (partial)
 * name and place a direct call — no dial-pad confirmation step, by design,
 * so this stays hands-free while driving. That also means a misheard name
 * can call the wrong person; findContactByName() returning Ambiguous instead
 * of guessing is the main safeguard against that.
 */
interface PhoneCallRepository {

    fun hasCallPermission(): Boolean

    suspend fun findContactByName(name: String): ContactLookupResult

    fun placeCall(phoneNumber: String)
}
