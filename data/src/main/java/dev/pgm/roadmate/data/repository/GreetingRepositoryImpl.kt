package dev.pgm.roadmate.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.data.datasource.local.roadMatePreferencesDataStore
import dev.pgm.roadmate.domain.repository.GreetingRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

class GreetingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : GreetingRepository {

    override suspend fun shouldGreetToday(): Boolean {
        val lastGreetedDate = context.roadMatePreferencesDataStore.data.first()[LAST_GREETED_DATE_KEY]
        return lastGreetedDate != LocalDate.now().toString()
    }

    override suspend fun markGreetedToday() {
        context.roadMatePreferencesDataStore.edit { prefs ->
            prefs[LAST_GREETED_DATE_KEY] = LocalDate.now().toString()
        }
    }

    private companion object {
        val LAST_GREETED_DATE_KEY = stringPreferencesKey("last_greeted_date")
    }
}
