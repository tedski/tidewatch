package com.tidewatch.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.tidewatch.data.models.Station
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Station entities.
 */
@Dao
interface StationDao {

    /**
     * Get a station by its ID.
     */
    @Query("SELECT * FROM stations WHERE id = :stationId LIMIT 1")
    suspend fun getStationById(stationId: String): Station?

    /**
     * Get a station by ID as a Flow (for observing changes).
     */
    @Query("SELECT * FROM stations WHERE id = :stationId LIMIT 1")
    fun getStationByIdFlow(stationId: String): Flow<Station?>

    /**
     * Get all stations.
     */
    @Query("SELECT * FROM stations ORDER BY name ASC")
    suspend fun getAllStations(): List<Station>

    /**
     * Get all stations as a Flow.
     */
    @Query("SELECT * FROM stations ORDER BY name ASC")
    fun getAllStationsFlow(): Flow<List<Station>>

    /**
     * Get stations by state.
     */
    @Query("SELECT * FROM stations WHERE state = :state ORDER BY name ASC")
    suspend fun getStationsByState(state: String): List<Station>

    /**
     * Get all unique states.
     */
    @Query("SELECT DISTINCT state FROM stations ORDER BY state ASC")
    suspend fun getAllStates(): List<String>

    /**
     * Search stations by name (case-insensitive).
     */
    @Query("""
        SELECT * FROM stations
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT :limit
    """)
    suspend fun searchStationsByName(query: String, limit: Int = 50): List<Station>

    /**
     * Get stations within a geographic bounding box.
     * Used for location-based search.
     */
    @Query("""
        SELECT * FROM stations
        WHERE latitude BETWEEN :minLat AND :maxLat
        AND longitude BETWEEN :minLon AND :maxLon
        ORDER BY
            ((latitude - :centerLat) * (latitude - :centerLat) +
             (longitude - :centerLon) * (longitude - :centerLon))
        LIMIT :limit
    """)
    suspend fun getStationsInBounds(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        centerLat: Double,
        centerLon: Double,
        limit: Int = 20
    ): List<Station>

    /**
     * Get harmonic (primary) stations only.
     */
    @Query("SELECT * FROM stations WHERE type = 'harmonic' ORDER BY name ASC")
    suspend fun getHarmonicStations(): List<Station>

    /**
     * Get subordinate stations only.
     */
    @Query("SELECT * FROM stations WHERE type = 'subordinate' ORDER BY name ASC")
    suspend fun getSubordinateStations(): List<Station>

    /**
     * Get subordinate stations for a specific reference station.
     */
    @Query("""
        SELECT * FROM stations
        WHERE type = 'subordinate'
        AND referenceStationId = :referenceStationId
        ORDER BY name ASC
    """)
    suspend fun getSubordinateStationsByReference(referenceStationId: String): List<Station>

    /**
     * Count total stations.
     */
    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getStationCount(): Int
}
