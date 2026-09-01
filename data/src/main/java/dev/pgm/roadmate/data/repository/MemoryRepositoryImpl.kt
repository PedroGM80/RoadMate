package dev.pgm.roadmate.data.repository

import dev.pgm.roadmate.data.db.MemoryDao
import dev.pgm.roadmate.data.db.TripExchangeEntity
import dev.pgm.roadmate.data.db.UserFactEntity
import dev.pgm.roadmate.domain.model.Exchange
import dev.pgm.roadmate.domain.model.FactType
import dev.pgm.roadmate.domain.model.UserFact
import dev.pgm.roadmate.domain.repository.MemoryRepository
import javax.inject.Inject

class MemoryRepositoryImpl @Inject constructor(
    private val dao: MemoryDao
) : MemoryRepository {

    override suspend fun recordExchange(question: String, answer: String) {
        val now = System.currentTimeMillis()
        dao.insertExchange(TripExchangeEntity(question = question, answer = answer, at = now))
        dao.pruneExchangesBefore(now - RETENTION_MS)
    }

    override suspend fun recentExchanges(limit: Int): List<Exchange> {
        val since = System.currentTimeMillis() - CONVERSATION_WINDOW_MS
        return dao.recentExchanges(since = since, limit = limit)
            .asReversed() // DAO returns newest-first; the prompt reads chronologically
            .map { Exchange(question = it.question, answer = it.answer) }
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
        else dao.deleteFactsMatching(type.name, valueContains)

    private companion object {
        /** How far back still counts as "this conversation". */
        const val CONVERSATION_WINDOW_MS = 2 * 60 * 60 * 1000L

        /** Older rows are dropped — the history is for continuity, not a log. */
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
