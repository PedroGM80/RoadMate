package dev.pgm.roadmate.domain.model

/** Which of a contact's numbers this is, so a follow-up can say "la de trabajo". */
enum class PhoneLabel(val spoken: String) {
    MOBILE("el móvil"),
    WORK("el del trabajo"),
    HOME("el de casa"),
    MAIN("el principal"),
    OTHER("otro"),
}

data class ContactMatch(
    val name: String,
    val phoneNumber: String,
    val label: PhoneLabel = PhoneLabel.OTHER,
)

sealed interface ContactLookupResult {
    data class Found(val contact: ContactMatch) : ContactLookupResult
    data class Ambiguous(val matches: List<ContactMatch>) : ContactLookupResult
    data object NotFound : ContactLookupResult
}
