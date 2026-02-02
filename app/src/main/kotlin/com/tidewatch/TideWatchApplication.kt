package com.tidewatch

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tidewatch.data.PreferencesRepository
import com.tidewatch.data.StationRepository
import com.tidewatch.data.TideDatabase
import com.tidewatch.tide.HarmonicCalculator
import com.tidewatch.tide.TideCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Application class providing manual dependency injection.
 *
 * All dependencies are lazy-initialized singletons accessible throughout the app.
 */
class TideWatchApplication : Application() {

    // Application-scoped coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Lazy-initialized database instance.
     */
    val database: TideDatabase by lazy {
        TideDatabase.getInstance(this)
    }

    /**
     * Lazy-initialized station repository.
     */
    val repository: StationRepository by lazy {
        StationRepository(database)
    }

    /**
     * Lazy-initialized harmonic calculator.
     *
     * Loads constituents and offsets from database on first access.
     */
    val calculator: HarmonicCalculator by lazy {
        createHarmonicCalculator()
    }

    /**
     * Lazy-initialized tide cache.
     */
    val cache: TideCache by lazy {
        TideCache(calculator)
    }

    /**
     * Lazy-initialized preferences repository.
     */
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(dataStore)
    }

    /**
     * Create a HarmonicCalculator by loading all constituents and offsets from database.
     *
     * This is called once on first access to the calculator.
     */
    private fun createHarmonicCalculator(): HarmonicCalculator = runBlocking {
        // Load all constituents grouped by station
        val allConstituents = database.harmonicConstituentDao().getAllConstituents()
        val constituentsByStation = allConstituents.groupBy { it.stationId }

        // Load all subordinate offsets
        val allOffsets = database.subordinateOffsetDao().getAllOffsets()
        val offsetsByStation = allOffsets.associateBy { it.stationId }

        HarmonicCalculator(
            constituents = constituentsByStation,
            subordinateOffsets = offsetsByStation
        )
    }
}

/**
 * DataStore for preferences.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tidewatch_preferences")
