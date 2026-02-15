package com.tidewatch.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a tide station with location and metadata.
 *
 * @property id NOAA station ID (e.g., "9414290")
 * @property name Human-readable station name (e.g., "San Francisco, CA")
 * @property state State or region (e.g., "California")
 * @property latitude Latitude in decimal degrees
 * @property longitude Longitude in decimal degrees
 * @property type Station type: "harmonic" (primary) or "subordinate"
 * @property timezoneOffset UTC offset in minutes for display purposes (e.g., -480 for PST)
 * @property referenceStationId For subordinate stations, the ID of the reference station
 */
@Entity(tableName = "stations")
data class Station(
    @PrimaryKey
    val id: String,
    val name: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
    val type: String, // "harmonic" or "subordinate"
    val timezoneOffset: Int, // UTC offset in minutes
    val referenceStationId: String? = null, // For subordinate stations
    val datumOffset: Double = 0.0 // Z₀: height of MSL above MLLW in feet
) {
    companion object {
        const val TYPE_HARMONIC = "harmonic"
        const val TYPE_SUBORDINATE = "subordinate"
    }

    /**
     * Returns true if this is a harmonic (primary) station.
     */
    fun isHarmonic(): Boolean = type == TYPE_HARMONIC

    /**
     * Returns true if this is a subordinate station.
     */
    fun isSubordinate(): Boolean = type == TYPE_SUBORDINATE
}
