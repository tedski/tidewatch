package com.tidewatch.data.models

import java.time.Instant

/**
 * Represents a tide extremum (high or low tide).
 *
 * This is not a database entity but a calculated result stored in cache.
 *
 * @property time UTC timestamp of the extremum
 * @property height Tide height in feet or meters (relative to MLLW datum)
 * @property type Type of extremum (HIGH or LOW)
 */
data class TideExtremum(
    val time: Instant,
    val height: Double, // in feet or meters
    val type: Type
) {
    enum class Type {
        HIGH,
        LOW
    }

    /**
     * Returns true if this is a high tide.
     */
    fun isHigh(): Boolean = type == Type.HIGH

    /**
     * Returns true if this is a low tide.
     */
    fun isLow(): Boolean = type == Type.LOW
}
