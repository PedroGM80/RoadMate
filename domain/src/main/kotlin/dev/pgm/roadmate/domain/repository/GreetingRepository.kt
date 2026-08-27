package dev.pgm.roadmate.domain.repository

/** Tracks whether RoadMate has already spoken its greeting today. */
interface GreetingRepository {
    suspend fun shouldGreetToday(): Boolean
    suspend fun markGreetedToday()
}
