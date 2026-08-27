package dev.pgm.roadmate.domain.model

data class ContactMatch(val name: String, val phoneNumber: String)

sealed interface ContactLookupResult {
    data class Found(val contact: ContactMatch) : ContactLookupResult
    data class Ambiguous(val matches: List<ContactMatch>) : ContactLookupResult
    data object NotFound : ContactLookupResult
}
