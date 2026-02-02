package com.tidewatch.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Represents a harmonic constituent for a tide station.
 *
 * A constituent is a single sinusoidal component of the tide, derived from
 * gravitational forces of the moon, sun, and astronomical cycles.
 *
 * @property stationId Foreign key to the station
 * @property constituentName Name of the constituent (e.g., "M2", "S2", "K1")
 * @property amplitude Amplitude in feet or meters (station-specific)
 * @property phaseLocal Phase angle in degrees (local to the station)
 */
@Entity(
    tableName = "harmonic_constituents",
    primaryKeys = ["stationId", "constituentName"],
    foreignKeys = [
        ForeignKey(
            entity = Station::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("stationId")]
)
data class HarmonicConstituent(
    val stationId: String,
    val constituentName: String,
    val amplitude: Double, // in feet or meters
    val phaseLocal: Double // in degrees (0-360)
)
