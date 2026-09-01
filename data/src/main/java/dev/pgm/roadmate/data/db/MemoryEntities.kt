package dev.pgm.roadmate.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One question/answer pair, timestamped so stale history can be dropped. */
@Entity(tableName = "trip_exchange")
data class TripExchangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val answer: String,
    val at: Long,
)

/**
 * A durable fact about the driver — home/work, a stated preference, a
 * frequent place, a "X es mi hermano" relationship. [factKey] disambiguates
 * within a [type] (a contact name for a relationship, null for a single-value
 * type like HOME); [hits] tracks how often it's been used.
 *
 * Only conversation history is written in this first cut — the fact table is
 * defined now so later slices add rows, not a schema migration.
 */
@Entity(
    tableName = "user_fact",
    indices = [Index(value = ["type", "factKey"], unique = true)],
)
data class UserFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val factKey: String? = null,
    val value: String,
    val updatedAt: Long,
    val hits: Int = 0,
)
