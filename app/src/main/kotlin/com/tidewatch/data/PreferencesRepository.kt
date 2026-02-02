package com.tidewatch.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing user preferences using DataStore.
 *
 * Provides type-safe access to persisted preferences like selected station and unit preference.
 *
 * @property dataStore The DataStore instance for preferences
 */
class PreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {

    /**
     * Preference keys.
     */
    object PreferencesKeys {
        val SELECTED_STATION_ID = stringPreferencesKey("selected_station_id")
        val USE_METRIC = booleanPreferencesKey("use_metric")
    }

    /**
     * Flow of the selected station ID.
     */
    val selectedStationId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SELECTED_STATION_ID]
    }

    /**
     * Set the selected station ID.
     */
    suspend fun setSelectedStationId(id: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_STATION_ID] = id
        }
    }

    /**
     * Flow of the unit preference.
     * Returns true for metric (meters), false for imperial (feet).
     */
    val useMetric: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USE_METRIC] ?: false // Default to feet
    }

    /**
     * Set the unit preference.
     *
     * @param metric True for metric (meters), false for imperial (feet)
     */
    suspend fun setUseMetric(metric: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_METRIC] = metric
        }
    }
}
