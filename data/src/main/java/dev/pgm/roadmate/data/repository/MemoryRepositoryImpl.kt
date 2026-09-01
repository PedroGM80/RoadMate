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
        if (dao.findFact(fact.type.name, fact.value) != null) return
        dao.insertFact(
            UserFactEntity(
                type = fact.type.name,
                factKey = fact.key,
                value = fact.value,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun facts(type: FactType): List<UserFact> =
        dao.factsByType(type.name).map { UserFact(FactType.valueOf(it.type), it.factKey, it.value) }

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
