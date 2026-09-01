package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.AnswerStyle
import kotlinx.coroutines.flow.Flow

/**
 * Small, growing bag of "how this driver likes RoadMate to behave",
 * persisted locally. Today: how long answers should be.
 */
interface AssistantPreferencesRepository {
    val answerStyle: Flow<AnswerStyle>
    suspend fun setAnswerStyle(style: AnswerStyle)
}
