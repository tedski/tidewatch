package com.tidewatch.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents time and height offsets for a subordinate station.
 *
 * Subordinate stations don't have their own harmonic constituents. Instead,
 * they reference a primary (harmonic) station and apply offsets to adjust
 * the tide predictions.
 *
 * @property stationId Foreign key to the subordinate station
 * @property referenceStationId Foreign key to the reference (harmonic) station
 * @property timeOffsetHigh Time offset for high tides in minutes
 * @property timeOffsetLow Time offset for low tides in minutes
 * @property heightOffsetHigh Height multiplier for high tides
 * @property heightOffsetLow Height multiplier for low tides
 */
@Entity(
    tableName = "subordinate_offsets",
    foreignKeys = [
        ForeignKey(
            entity = Station::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Station::class,
            parentColumns = ["id"],
            childColumns = ["referenceStationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("stationId"), Index("referenceStationId")]
)
data class SubordinateOffset(
    @PrimaryKey
    val stationId: String,
    val referenceStationId: String,
    val timeOffsetHigh: Int, // minutes
    val timeOffsetLow: Int, // minutes
    val heightOffsetHigh: Double, // multiplier
    val heightOffsetLow: Double // multiplier
)
