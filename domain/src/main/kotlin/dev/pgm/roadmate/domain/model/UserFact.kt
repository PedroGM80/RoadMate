package dev.pgm.roadmate.domain.model

/** What kind of thing RoadMate remembers about the driver. */
enum class FactType {
    /** A stated like/dislike/instruction: "no me gustan las autovías". */
    PREFERENCE,

    /** The driver's home location. */
    HOME,

    /** The driver's work location. */
    WORK,

    /** A place the driver goes to often (built up from map searches). */
    PLACE,

    /** "X es mi hermano" — [key] is the relationship word, [value] the name. */
    RELATIONSHIP,
}

/**
 * One durable fact. [key] disambiguates within a [type] (a relationship word,
 * or null for single-value types); [value] is the human-readable content used
 * verbatim in prompts and spoken back.
 */
data class UserFact(
    val type: FactType,
    val key: String? = null,
    val value: String,
)
