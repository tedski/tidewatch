package com.tidewatch.data.models

import java.time.Instant

/**
 * Represents a calculated tide height at a specific time.
 *
 * This is a calculation result, not a database entity.
 *
 * @property time UTC timestamp
 * @property height Tide height in feet or meters (relative to MLLW datum)
 * @property rateOfChange Rate of height change in feet/hour or meters/hour
 * @property direction Tide direction (RISING, FALLING, or SLACK)
 */
data class TideHeight(
    val time: Instant,
    val height: Double, // in feet or meters
    val rateOfChange: Double, // in feet/hour or meters/hour
    val direction: Direction
) {
    enum class Direction {
        RISING,   // Tide is rising (flood tide)
        FALLING,  // Tide is falling (ebb tide)
        SLACK     // Tide is at or near extremum (minimal change)
    }

    companion object {
        /**
         * Threshold for considering a tide as "slack" (minimal change).
         * If rate of change is below this threshold, direction is SLACK.
         */
        const val SLACK_THRESHOLD = 0.05 // ft/hr or m/hr
    }

    /**
     * Returns true if the tide is rising.
     */
    fun isRising(): Boolean = direction == Direction.RISING

    /**
     * Returns true if the tide is falling.
     */
    fun isFalling(): Boolean = direction == Direction.FALLING

    /**
     * Returns true if the tide is slack (at or near extremum).
     */
    fun isSlack(): Boolean = direction == Direction.SLACK
}
