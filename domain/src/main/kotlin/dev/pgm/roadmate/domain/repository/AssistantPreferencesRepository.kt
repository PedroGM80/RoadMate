package dev.pgm.roadmate.domain.repository

import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

/**
 * Small, growing bag of "how this driver likes RoadMate to behave",
 * persisted locally: how long answers should be, light/dark, and whether the
 * "oye copiloto" wake phrase listens hands-free.
 */
interface AssistantPreferencesRepository {
    val answerStyle: Flow<AnswerStyle>
    suspend fun setAnswerStyle(style: AnswerStyle)

    val themePreference: Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)

    /** Whether the always-on wake phrase is active. Defaults to on. */
    val handsFreeEnabled: Flow<Boolean>
    suspend fun setHandsFreeEnabled(enabled: Boolean)

    /** Id of the chosen downloadable local-AI model; null until the driver picks one. */
    val localAiModelId: Flow<String?>
    suspend fun setLocalAiModelId(id: String)

    /** How fast the assistant speaks; 1.0 is normal. Adjusted by "más despacio" / "más rápido". */
    val speechRate: Flow<Float>
    suspend fun setSpeechRate(rate: Float)
}
