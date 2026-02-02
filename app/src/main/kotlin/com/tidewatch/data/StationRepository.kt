package com.tidewatch.data

import com.tidewatch.data.models.HarmonicConstituent
import com.tidewatch.data.models.Station
import com.tidewatch.data.models.SubordinateOffset
import kotlinx.coroutines.flow.Flow
import kotlin.math.*

/**
 * Repository for accessing tide station data.
 *
 * Provides a clean API for querying stations, constituents, and offsets.
 *
 * @property database The TideDatabase instance
 */
class StationRepository(
    private val database: TideDatabase
) {
    private val stationDao = database.stationDao()
    private val constituentDao = database.harmonicConstituentDao()
    private val offsetDao = database.subordinateOffsetDao()

    /**
     * Get a station by ID.
     */
    suspend fun getStation(stationId: String): Station? {
        return stationDao.getStationById(stationId)
    }

    /**
     * Get a station by ID as a Flow.
     */
    fun getStationFlow(stationId: String): Flow<Station?> {
        return stationDao.getStationByIdFlow(stationId)
    }

    /**
     * Get all stations.
     */
    suspend fun getAllStations(): List<Station> {
        return stationDao.getAllStations()
    }

    /**
     * Get stations by state.
     */
    suspend fun getStationsByState(state: String): List<Station> {
        return stationDao.getStationsByState(state)
    }

    /**
     * Get all states.
     */
    suspend fun getAllStates(): List<String> {
        return stationDao.getAllStates()
    }

    /**
     * Search stations by name.
     */
    suspend fun searchStations(query: String, limit: Int = 50): List<Station> {
        return stationDao.searchStationsByName(query, limit)
    }

    /**
     * Find nearest stations to a location.
     *
     * Uses great-circle distance (haversine formula).
     *
     * @param latitude User latitude
     * @param longitude User longitude
     * @param radiusMiles Search radius in miles
     * @param limit Maximum number of stations to return
     * @return List of stations sorted by distance
     */
    suspend fun findNearestStations(
        latitude: Double,
        longitude: Double,
        radiusMiles: Double = 100.0,
        limit: Int = 10
    ): List<StationWithDistance> {
        // Calculate bounding box for initial query
        val latDelta = radiusMiles / 69.0 // Approximately 69 miles per degree latitude
        val lonDelta = radiusMiles / abs(cos(Math.toRadians(latitude)) * 69.0)

        val minLat = latitude - latDelta
        val maxLat = latitude + latDelta
        val minLon = longitude - lonDelta
        val maxLon = longitude + lonDelta

        // Get stations in bounding box
        val stations = stationDao.getStationsInBounds(
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon,
            centerLat = latitude,
            centerLon = longitude,
            limit = limit * 2 // Get more than needed for accurate sorting
        )

        // Calculate great-circle distances and sort
        return stations
            .map { station ->
                StationWithDistance(
                    station = station,
                    distanceMiles = calculateDistance(
                        latitude, longitude,
                        station.latitude, station.longitude
                    )
                )
            }
            .filter { it.distanceMiles <= radiusMiles }
            .sortedBy { it.distanceMiles }
            .take(limit)
    }

    /**
     * Get harmonic constituents for a station.
     */
    suspend fun getConstituents(stationId: String): List<HarmonicConstituent> {
        return constituentDao.getConstituentsForStation(stationId)
    }

    /**
     * Get subordinate offset for a station.
     */
    suspend fun getSubordinateOffset(stationId: String): SubordinateOffset? {
        return offsetDao.getOffsetForStation(stationId)
    }

    /**
     * Get the reference station for a subordinate station.
     */
    suspend fun getReferenceStation(subordinateStation: Station): Station? {
        if (!subordinateStation.isSubordinate()) {
            return null
        }
        val referenceId = subordinateStation.referenceStationId ?: return null
        return getStation(referenceId)
    }

    /**
     * Calculate great-circle distance between two points using haversine formula.
     *
     * @param lat1 First point latitude
     * @param lon1 First point longitude
     * @param lat2 Second point latitude
     * @param lon2 Second point longitude
     * @return Distance in miles
     */
    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusMiles = 3958.8

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * asin(sqrt(a))

        return earthRadiusMiles * c
    }

    /**
     * Station with calculated distance from a reference point.
     */
    data class StationWithDistance(
        val station: Station,
        val distanceMiles: Double
    )
}
