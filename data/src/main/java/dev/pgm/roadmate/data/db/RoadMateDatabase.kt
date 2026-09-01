package dev.pgm.roadmate.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The single on-device store for everything RoadMate remembers about the
 * driver. Local only — nothing here is ever transmitted.
 */
@Database(
    entities = [TripExchangeEntity::class, UserFactEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RoadMateDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}
