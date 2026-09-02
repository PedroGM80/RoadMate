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
    /**
     * The contact's own name for this number when they typed one ("Coche",
     * "Casa de mis padres"). Android stores those as a free-text label
     * alongside TYPE_CUSTOM, and ignoring it meant every custom number came
     * back as an unnameable "otro" — so RoadMate could neither offer it
     * ("¿el móvil o el coche?") nor understand the answer.
     */
    val customLabel: String? = null,
) {
    /** How to say this number out loud: the contact's own word for it wins. */
    val spokenLabel: String
        get() = customLabel?.takeIf { it.isNotBlank() }?.let { "el de ${it.lowercase()}" }
            ?: label.spoken
}

sealed interface ContactLookupResult {
    data class Found(val contact: ContactMatch) : ContactLookupResult
    data class Ambiguous(val matches: List<ContactMatch>) : ContactLookupResult
    data object NotFound : ContactLookupResult
}
