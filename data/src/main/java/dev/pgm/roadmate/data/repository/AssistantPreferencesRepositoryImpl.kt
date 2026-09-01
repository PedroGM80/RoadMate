package dev.pgm.roadmate.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
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

    private companion object {
        val ANSWER_STYLE_KEY = stringPreferencesKey("answer_style")
        val THEME_KEY = stringPreferencesKey("theme_preference")

        inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
            runCatching { enumValueOf<T>(name) }.getOrNull()
    }
}
