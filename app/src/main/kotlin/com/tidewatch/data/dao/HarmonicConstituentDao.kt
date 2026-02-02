package com.tidewatch.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.tidewatch.data.models.HarmonicConstituent

/**
 * Data Access Object for HarmonicConstituent entities.
 */
@Dao
interface HarmonicConstituentDao {

    /**
     * Get all harmonic constituents for a station.
     */
    @Query("SELECT * FROM harmonic_constituents WHERE stationId = :stationId")
    suspend fun getConstituentsForStation(stationId: String): List<HarmonicConstituent>

    /**
     * Get a specific constituent for a station.
     */
    @Query("""
        SELECT * FROM harmonic_constituents
        WHERE stationId = :stationId
        AND constituentName = :constituentName
        LIMIT 1
    """)
    suspend fun getConstituent(
        stationId: String,
        constituentName: String
    ): HarmonicConstituent?

    /**
     * Get constituent count for a station.
     */
    @Query("SELECT COUNT(*) FROM harmonic_constituents WHERE stationId = :stationId")
    suspend fun getConstituentCount(stationId: String): Int

    /**
     * Get all unique constituent names in the database.
     */
    @Query("SELECT DISTINCT constituentName FROM harmonic_constituents ORDER BY constituentName")
    suspend fun getAllConstituentNames(): List<String>

    /**
     * Get all harmonic constituents for all stations.
     */
    @Query("SELECT * FROM harmonic_constituents")
    suspend fun getAllConstituents(): List<HarmonicConstituent>
}
