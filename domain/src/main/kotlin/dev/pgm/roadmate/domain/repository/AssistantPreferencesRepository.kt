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

    /**
     * Whether routing-map tiles may be downloaded over mobile data. Defaults
     * to on: the tiles are only needed for an area the driver is actually in,
     * and a car in that area is on mobile data by definition — waiting for
     * Wi-Fi meant routing never worked on the road at all. Off is for drivers
     * on a tight data plan, who can pre-download at home instead.
     */
    val routeDataOverMobile: Flow<Boolean>
    suspend fun setRouteDataOverMobile(enabled: Boolean)
}
