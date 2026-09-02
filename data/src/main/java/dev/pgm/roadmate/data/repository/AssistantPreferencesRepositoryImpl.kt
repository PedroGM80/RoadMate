package dev.pgm.roadmate.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.data.datasource.local.roadMatePreferencesDataStore
import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.ThemePreference
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AssistantPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AssistantPreferencesRepository {

    override val answerStyle: Flow<AnswerStyle> =
        context.roadMatePreferencesDataStore.data.map { prefs ->
            prefs[ANSWER_STYLE_KEY]?.let { enumOrNull<AnswerStyle>(it) } ?: AnswerStyle.DEFAULT
        }

    override suspend fun setAnswerStyle(style: AnswerStyle) {
        context.roadMatePreferencesDataStore.edit { it[ANSWER_STYLE_KEY] = style.name }
    }

    override val themePreference: Flow<ThemePreference> =
        context.roadMatePreferencesDataStore.data.map { prefs ->
            prefs[THEME_KEY]?.let { enumOrNull<ThemePreference>(it) } ?: ThemePreference.DEFAULT
        }

    override suspend fun setThemePreference(preference: ThemePreference) {
        context.roadMatePreferencesDataStore.edit { it[THEME_KEY] = preference.name }
    }

    override val handsFreeEnabled: Flow<Boolean> =
        context.roadMatePreferencesDataStore.data.map { prefs ->
            prefs[HANDS_FREE_KEY] ?: true
        }

    override suspend fun setHandsFreeEnabled(enabled: Boolean) {
        context.roadMatePreferencesDataStore.edit { it[HANDS_FREE_KEY] = enabled }
    }

    override val localAiModelId: Flow<String?> =
        context.roadMatePreferencesDataStore.data.map { prefs -> prefs[LOCAL_AI_MODEL_KEY] }

    override suspend fun setLocalAiModelId(id: String) {
        context.roadMatePreferencesDataStore.edit { it[LOCAL_AI_MODEL_KEY] = id }
    }

    override val speechRate: Flow<Float> =
        context.roadMatePreferencesDataStore.data.map { prefs ->
            (prefs[SPEECH_RATE_KEY] ?: 1.0f).coerceIn(MIN_RATE, MAX_RATE)
        }

    override suspend fun setSpeechRate(rate: Float) {
        context.roadMatePreferencesDataStore.edit {
            it[SPEECH_RATE_KEY] = rate.coerceIn(MIN_RATE, MAX_RATE)
        }
    }

    private companion object {
        val ANSWER_STYLE_KEY = stringPreferencesKey("answer_style")
        val THEME_KEY = stringPreferencesKey("theme_preference")
        val HANDS_FREE_KEY = booleanPreferencesKey("hands_free_enabled")
        val LOCAL_AI_MODEL_KEY = stringPreferencesKey("local_ai_model_id")
        val SPEECH_RATE_KEY = floatPreferencesKey("speech_rate")
        const val MIN_RATE = 0.6f
        const val MAX_RATE = 1.6f

        inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
            runCatching { enumValueOf<T>(name) }.getOrNull()
    }
}
