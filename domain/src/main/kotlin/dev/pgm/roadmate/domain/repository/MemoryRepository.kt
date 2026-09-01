package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.Exchange

/**
 * RoadMate's on-device memory: what it has learned about this driver and the
 * current conversation. All local, never transmitted. Grows over time — for
 * now it holds the recent question/answer history so replies can build on
 * what was just said instead of treating every question as the first.
 */
interface MemoryRepository {

    /** Stores a completed question/answer pair. */
    suspend fun recordExchange(question: String, answer: String)

    /**
     * The last [limit] exchanges from the current conversation (recent
     * enough to still be relevant — stale history is dropped), newest last.
     */
    suspend fun recentExchanges(limit: Int = 3): List<Exchange>
}
