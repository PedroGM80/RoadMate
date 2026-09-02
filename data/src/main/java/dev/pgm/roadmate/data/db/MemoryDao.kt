package dev.pgm.roadmate.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {

    @Insert
    suspend fun insertExchange(exchange: TripExchangeEntity): Long

    /** Newest first, only those since [since]. */
    @Query("SELECT * FROM trip_exchange WHERE at >= :since ORDER BY at DESC LIMIT :limit")
    suspend fun recentExchanges(since: Long, limit: Int): List<TripExchangeEntity>

    @Query("DELETE FROM trip_exchange WHERE at < :before")
    suspend fun pruneExchangesBefore(before: Long)

    /** Newest first, no time cutoff — for keyword search over recent history. */
    @Query("SELECT * FROM trip_exchange ORDER BY at DESC LIMIT :limit")
    suspend fun latestExchanges(limit: Int): List<TripExchangeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFact(fact: UserFactEntity): Long

    @Query("SELECT * FROM user_fact WHERE type = :type AND value = :value LIMIT 1")
    suspend fun findFact(type: String, value: String): UserFactEntity?

    @Query("SELECT * FROM user_fact WHERE type = :type ORDER BY updatedAt DESC")
    suspend fun factsByType(type: String): List<UserFactEntity>

    @Query("SELECT * FROM user_fact WHERE type = :type ORDER BY hits DESC, updatedAt DESC LIMIT :limit")
    suspend fun topFactsByType(type: String, limit: Int): List<UserFactEntity>

    @Query("UPDATE user_fact SET hits = hits + 1, updatedAt = :now WHERE type = :type AND value = :value")
    suspend fun bumpFact(type: String, value: String, now: Long): Int

    @Query("DELETE FROM user_fact WHERE type = :type")
    suspend fun deleteFactsByType(type: String): Int

    @Query("DELETE FROM user_fact WHERE type = :type AND factKey = :key")
    suspend fun deleteFactByKey(type: String, key: String)

    @Query("DELETE FROM trip_exchange")
    suspend fun clearExchanges()

    @Query("DELETE FROM user_fact")
    suspend fun clearFacts()

    /**
     * "olvida lo de X". [match] is escaped by the caller and the pattern says
     * so: it arrives straight from a speech transcript, and an unescaped `%`
     * in a mis-heard word would match every fact of that type and delete the
     * lot — the driver asked to forget one thing, not everything.
     */
    @Query(
        "DELETE FROM user_fact WHERE type = :type " +
            "AND value LIKE '%' || :match || '%' ESCAPE '\\'"
    )
    suspend fun deleteFactsMatching(type: String, match: String): Int
}
