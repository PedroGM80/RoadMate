package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.UserFact

/**
 * RoadMate's on-device memory: what it has learned about this driver and the
 * current conversation. All local, never transmitted. Grows over time —
 * conversation history for continuity, plus durable facts (preferences,
 * home/work, frequent places, relationships).
 */
interface MemoryRepository {

    /** Stores a completed question/answer pair. */
    suspend fun recordExchange(question: String, answer: String)

    /**
     * The last [limit] exchanges from the current conversation (recent
     * enough to still be relevant — stale history is dropped), newest last.
     */
    suspend fun recentExchanges(limit: Int = 3): List<Exchange>

    /**
     * Past exchanges whose question or answer mentions [term], newest first —
     * for "¿qué te dije sobre…?". Plain substring match; the history table is
     * small (weekly prune) so it needs no full-text index.
     */
    suspend fun searchExchanges(term: String, limit: Int = 3): List<Exchange>

    /** Adds a fact, or updates the existing one with the same type + key/value. */
    suspend fun remember(fact: UserFact)

    /** Notes that the driver headed to [place] — inserts it or bumps its count. */
    suspend fun rememberPlace(place: String)

    /** Every stored fact of [type]. */
    suspend fun facts(type: FactType): List<UserFact>

    /** The [limit] places the driver goes to most, most-visited first. */
    suspend fun frequentPlaces(limit: Int = 3): List<UserFact>

    /** Wipes everything — conversation history and every learned fact. */
    suspend fun clearAll()

    /**
     * Drops facts of [type] whose value contains [valueContains]
     * (case-insensitive); pass null to drop them all. Returns how many went.
     */
    suspend fun forget(type: FactType, valueContains: String? = null): Int
}
