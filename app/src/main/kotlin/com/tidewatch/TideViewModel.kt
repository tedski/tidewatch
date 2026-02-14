package com.tidewatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidewatch.data.PreferencesRepository
import com.tidewatch.data.StationRepository
import com.tidewatch.data.models.Station
import com.tidewatch.data.models.TideExtremum
import com.tidewatch.data.models.TideHeight
import com.tidewatch.tide.HarmonicCalculator
import com.tidewatch.tide.TideCache
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Core ViewModel for tide data and UI state management.
 *
 * Orchestrates repository, calculator, and cache to provide reactive UI state.
 *
 * @property repository Station repository for database access
 * @property application Application instance for async calculator/cache access
 * @property preferencesRepository Preferences repository for persisted settings
 */
class TideViewModel(
    private val repository: StationRepository,
    private val application: TideWatchApplication,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    /**
     * UI state sealed class.
     */
    sealed class TideUiState {
        object Loading : TideUiState()
        object NoStationSelected : TideUiState()
        data class Success(
            val station: Station,
            val currentHeight: TideHeight,
            val nextHigh: TideExtremum?,
            val nextLow: TideExtremum?,
            val curve24h: List<TideHeight>,
            val extrema7d: List<TideExtremum>
        ) : TideUiState()
        data class Error(val message: String) : TideUiState()
    }

    // Internal mutable state
    private val _state = MutableStateFlow<TideUiState>(TideUiState.Loading)
    val state: StateFlow<TideUiState> = _state.asStateFlow()

    // Selected station ID from preferences
    val selectedStationId: StateFlow<String?> = preferencesRepository.selectedStationId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Unit preference from preferences
    val useMetric: StateFlow<Boolean> = preferencesRepository.useMetric
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Nearby stations for picker
    private val _nearbyStations = MutableStateFlow<List<StationRepository.StationWithDistance>>(emptyList())
    val nearbyStations: StateFlow<List<StationRepository.StationWithDistance>> = _nearbyStations.asStateFlow()

    // Browse stations for picker
    private val _browseStations = MutableStateFlow<List<Station>>(emptyList())
    val browseStations: StateFlow<List<Station>> = _browseStations.asStateFlow()

    // All states for browse mode
    private val _allStates = MutableStateFlow<List<String>>(emptyList())
    val allStates: StateFlow<List<String>> = _allStates.asStateFlow()

    // Navigation state for station picker
    private val _pickerMode = MutableStateFlow<String?>(null)
    val pickerMode: StateFlow<String?> = _pickerMode.asStateFlow()

    private val _pickerSelectedState = MutableStateFlow<String?>(null)
    val pickerSelectedState: StateFlow<String?> = _pickerSelectedState.asStateFlow()

    init {
        // Load persisted state and tide data
        viewModelScope.launch {
            selectedStationId.collect { stationId ->
                if (stationId != null) {
                    loadTideData(stationId)
                } else {
                    _state.value = TideUiState.NoStationSelected
                }
            }
        }

        // Load all states for browse mode
        viewModelScope.launch {
            try {
                _allStates.value = repository.getAllStates()
            } catch (e: Exception) {
                // Silently fail, browse mode will show empty
            }
        }
    }

    /**
     * Load tide data for the selected station.
     */
    private suspend fun loadTideData(stationId: String) {
        try {
            _state.value = TideUiState.Loading

            // Get station
            val station = repository.getStation(stationId)
                ?: throw IllegalArgumentException("Station not found: $stationId")

            // Get calculator and cache (async initialization)
            val calculator = application.getCalculator()
            val cache = application.getCache()

            // Calculate current tide height
            val now = Instant.now()
            val currentHeight = calculator.calculateTideHeight(stationId, now)

            // Get next high and low from cache
            val nextHigh = cache.getNextHigh(stationId, now)
            val nextLow = cache.getNextLow(stationId, now)

            // Generate 24-hour curve
            val endTime24h = now.plus(24, ChronoUnit.HOURS)
            val curve24h = calculator.generateTideCurve(
                stationId = stationId,
                startTime = now,
                endTime = endTime24h,
                intervalMinutes = 10
            )

            // Get 7-day extrema from cache
            val extrema7d = cache.getAllExtrema(stationId)

            _state.value = TideUiState.Success(
                station = station,
                currentHeight = currentHeight,
                nextHigh = nextHigh,
                nextLow = nextLow,
                curve24h = curve24h,
                extrema7d = extrema7d
            )
        } catch (e: Exception) {
            _state.value = TideUiState.Error(
                message = e.message ?: "Failed to load tide data"
            )
        }
    }

    /**
     * Refresh current height only (for periodic updates).
     */
    fun refreshCurrentHeight() {
        val currentState = _state.value
        if (currentState !is TideUiState.Success) return

        viewModelScope.launch {
            try {
                val calculator = application.getCalculator()
                val stationId = currentState.station.id
                val now = Instant.now()
                val currentHeight = calculator.calculateTideHeight(stationId, now)

                // Update state with new current height
                _state.value = currentState.copy(currentHeight = currentHeight)
            } catch (e: Exception) {
                // Silently fail, keep existing state
            }
        }
    }

    /**
     * Select a new station.
     */
    fun selectStation(stationId: String) {
        viewModelScope.launch {
            try {
                // Save to preferences
                preferencesRepository.setSelectedStationId(stationId)

                // Pre-warm cache for this station
                val cache = application.getCache()
                cache.prewarm(stationId)

                // Data will be loaded automatically via selectedStationId flow
            } catch (e: Exception) {
                _state.value = TideUiState.Error(
                    message = "Failed to select station: ${e.message}"
                )
            }
        }
    }

    /**
     * Load nearby stations based on GPS location.
     */
    fun loadNearbyStations(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                val stations = repository.findNearestStations(
                    latitude = latitude,
                    longitude = longitude,
                    radiusMiles = 100.0,
                    limit = 20
                )
                _nearbyStations.value = stations
            } catch (e: Exception) {
                // Silently fail, show empty list
                _nearbyStations.value = emptyList()
            }
        }
    }

    /**
     * Load stations by state for browse mode.
     */
    fun loadStationsByState(state: String) {
        viewModelScope.launch {
            try {
                val stations = repository.getStationsByState(state)
                _browseStations.value = stations
            } catch (e: Exception) {
                // Silently fail, show empty list
                _browseStations.value = emptyList()
            }
        }
    }

    /**
     * Set unit preference.
     */
    fun setUseMetric(metric: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setUseMetric(metric)
        }
    }

    /**
     * Save picker navigation state for restoration after swipe-back.
     */
    fun savePickerState(mode: String?, selectedState: String?) {
        _pickerMode.value = mode
        _pickerSelectedState.value = selectedState
    }

    /**
     * Clear picker navigation state.
     */
    fun clearPickerState() {
        _pickerMode.value = null
        _pickerSelectedState.value = null
    }
}
