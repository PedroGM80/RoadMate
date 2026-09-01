package dev.pgm.roadmate.data.db

import androidx.room.Dao
import androidx.room.Insert
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
}
