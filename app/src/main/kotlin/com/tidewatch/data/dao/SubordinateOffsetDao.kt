package com.tidewatch.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.tidewatch.data.models.SubordinateOffset

/**
 * Data Access Object for SubordinateOffset entities.
 */
@Dao
interface SubordinateOffsetDao {

    /**
     * Get the subordinate offset for a station.
     */
    @Query("SELECT * FROM subordinate_offsets WHERE stationId = :stationId LIMIT 1")
    suspend fun getOffsetForStation(stationId: String): SubordinateOffset?

    /**
     * Get all subordinate offsets.
     */
    @Query("SELECT * FROM subordinate_offsets")
    suspend fun getAllOffsets(): List<SubordinateOffset>

    /**
     * Get all subordinate offsets for a specific reference station.
     */
    @Query("SELECT * FROM subordinate_offsets WHERE referenceStationId = :referenceStationId")
    suspend fun getOffsetsByReference(referenceStationId: String): List<SubordinateOffset>
}
