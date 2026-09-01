package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

/**
 * Small, growing bag of "how this driver likes RoadMate to behave",
 * persisted locally: how long answers should be, and light/dark.
 */
interface AssistantPreferencesRepository {
    val answerStyle: Flow<AnswerStyle>
    suspend fun setAnswerStyle(style: AnswerStyle)

    val themePreference: Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)
}
