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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

/**
 * Application class providing manual dependency injection.
 *
 * All dependencies are lazy-initialized singletons accessible throughout the app.
 */
class TideWatchApplication : Application() {

    // Application-scoped coroutine scope for async initialization
    // Uses IO dispatcher for database operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }

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
     * Deferred initialization of harmonic calculator.
     *
     * Loads constituents and offsets from database asynchronously to avoid blocking app startup.
     */
    private val calculatorDeferred: Deferred<HarmonicCalculator> by lazy {
        applicationScope.async {
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
     * Get the harmonic calculator, suspending until initialization completes if necessary.
     *
     * First call triggers database I/O to load all constituents and offsets.
     * Subsequent calls return immediately with the cached instance.
     *
     * @throws Exception if database loading fails
     */
    suspend fun getCalculator(): HarmonicCalculator = calculatorDeferred.await()

    /**
     * Deferred initialization of tide cache.
     * Explicitly awaits calculator to avoid nested suspension race conditions.
     */
    private val cacheDeferred: Deferred<TideCache> by lazy {
        applicationScope.async {
            val calculator = calculatorDeferred.await()
            TideCache(calculator)
        }
    }

    /**
     * Get the tide cache, suspending until initialization completes if necessary.
     *
     * First call triggers calculator initialization and cache setup.
     * Subsequent calls return immediately with the cached instance.
     *
     * @throws Exception if initialization fails
     */
    suspend fun getCache(): TideCache = cacheDeferred.await()

    /**
     * Lazy-initialized preferences repository.
     */
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(dataStore)
    }
}

/**
 * DataStore for preferences.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tidewatch_preferences")
