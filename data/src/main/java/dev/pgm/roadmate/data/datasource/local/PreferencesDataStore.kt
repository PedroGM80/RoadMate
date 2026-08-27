package dev.pgm.roadmate.data.datasource.local

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Shared across every DataStore-backed repository — DataStore throws
 * "There are multiple DataStores active for this file" if two separate
 * `by preferencesDataStore(name = "roadmate_prefs")` delegates both touch
 * the same file, so this single extension property is the one source of it.
 */
val Context.roadMatePreferencesDataStore by preferencesDataStore(name = "roadmate_prefs")
