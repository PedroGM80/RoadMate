package dev.pgm.roadmate.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.data.datasource.local.roadMatePreferencesDataStore
import dev.pgm.roadmate.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OnboardingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : OnboardingRepository {

    override val isOnboardingCompleted = context.roadMatePreferencesDataStore.data
        .map { prefs -> prefs[ONBOARDING_COMPLETED_KEY] == true }

    override suspend fun setOnboardingCompleted() {
        context.roadMatePreferencesDataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED_KEY] = true }
    }

    private companion object {
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    }
}
