package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.data.db.MemoryDao
import dev.pgm.roadmate.data.db.TripExchangeEntity
import dev.pgm.roadmate.data.db.UserFactEntity
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.UserFact
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.utils.spanishRegex
import javax.inject.Inject

class MemoryRepositoryImpl @Inject constructor(
    private val dao: MemoryDao
) : MemoryRepository {

    override suspend fun recordExchange(question: String, answer: String) {
        val now = System.currentTimeMillis()
        val q = question.trim().take(MAX_EXCHANGE_CHARS)
        val a = answer.trim().take(MAX_EXCHANGE_CHARS)
        // Never feed obviously-broken output back into future prompts: an
        // "answer" carrying our own prompt scaffolding would compound every
        // turn (seen on-device — a weak model echoing the context block, then
        // that echo re-entering the next prompt).
        if (a.isBlank() || a.contains("Antes en esta conversación") || a.contains("<|im_")) return
        dao.insertExchange(TripExchangeEntity(question = q, answer = a, at = now))
        dao.pruneExchangesBefore(now - RETENTION_MS)
    }

    override suspend fun recentExchanges(limit: Int): List<Exchange> {
        val since = System.currentTimeMillis() - CONVERSATION_WINDOW_MS
        return dao.recentExchanges(since = since, limit = limit)
            .asReversed() // DAO returns newest-first; the prompt reads chronologically
            .map { Exchange(question = it.question, answer = it.answer) }
    }

    override suspend fun searchExchanges(term: String, limit: Int): List<Exchange> {
        // Unicode-aware split: an ASCII \W chops "Ronda" fine but turns
        // "Málaga" into "m", "laga", so recall on accented place names failed.
        val tokens = term.lowercase().split(WORD_SPLIT).filter { it.length >= 4 }
        if (tokens.isEmpty()) return emptyList()
        return dao.latestExchanges(SEARCH_SCAN)
            .map { row ->
                val hay = (row.question + " " + row.answer).lowercase()
                row to tokens.count { hay.contains(it) }
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<TripExchangeEntity, Int>> { it.second }.thenByDescending { it.first.at })
            .take(limit)
            .map { Exchange(question = it.first.question, answer = it.first.answer) }
    }

    override suspend fun remember(fact: UserFact) {
        // Keyed facts (e.g. a relationship) are single-valued: replace any
        // existing one for that key. Keyless facts just dedupe by value.
        val key = fact.key
        if (key != null) {
            dao.deleteFactByKey(fact.type.name, key)
        } else if (dao.findFact(fact.type.name, fact.value) != null) {
            return
        }
        dao.insertFact(
            UserFactEntity(
                type = fact.type.name,
                factKey = fact.key,
                value = fact.value,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun rememberPlace(place: String) {
        val now = System.currentTimeMillis()
        if (dao.bumpFact(FactType.PLACE.name, place, now) == 0) {
            dao.insertFact(
                UserFactEntity(type = FactType.PLACE.name, value = place, updatedAt = now, hits = 1)
            )
        }
    }

    override suspend fun facts(type: FactType): List<UserFact> =
        dao.factsByType(type.name).map { it.toDomain() }

    override suspend fun frequentPlaces(limit: Int): List<UserFact> =
        dao.topFactsByType(FactType.PLACE.name, limit).map { it.toDomain() }

    private fun UserFactEntity.toDomain() = UserFact(FactType.valueOf(type), factKey, value)

    override suspend fun forget(type: FactType, valueContains: String?): Int =
        if (valueContains.isNullOrBlank()) dao.deleteFactsByType(type.name)
        else dao.deleteFactsMatching(type.name, escapeLike(valueContains))

    /**
     * The text comes from a speech transcript and goes into a SQL LIKE
     * pattern. A stray `%` there means "everything", and this statement is a
     * DELETE.
     */
    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    override suspend fun clearAll() {
        dao.clearExchanges()
        dao.clearFacts()
    }

    private companion object {
        val WORD_SPLIT = spanishRegex("""\W+""", ignoreCase = false)

        /** How far back still counts as "still talking about the same thing".
         *  Short on purpose — a stale exchange fed back in makes a small model
         *  answer the old question. */
        const val CONVERSATION_WINDOW_MS = 5 * 60 * 1000L

        /** Older rows are dropped — the history is for continuity, not a log. */
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L

        /** How many recent rows a keyword search scans (weekly prune keeps this small). */
        const val SEARCH_SCAN = 200

        /** Hard cap per stored side of an exchange — continuity needs a gist, not a transcript. */
        const val MAX_EXCHANGE_CHARS = 240
    }
}
