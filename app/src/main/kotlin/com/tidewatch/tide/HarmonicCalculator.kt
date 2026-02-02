package com.tidewatch.tide

import com.tidewatch.data.models.*
import java.time.Duration
import java.time.Instant
import kotlin.math.*

/**
 * Core harmonic analysis engine for tide prediction.
 *
 * Implements the standard harmonic method used by NOAA:
 * h(t) = datum + Σ[A_i × f_i × cos(ω_i × t + φ_i - κ_i)]
 *
 * Where:
 * - A_i = constituent amplitude (from database)
 * - f_i = node factor (from astronomical calculation)
 * - ω_i = angular velocity (constituent speed)
 * - φ_i = local phase (from database)
 * - κ_i = equilibrium argument (from astronomical calculation)
 *
 * @property constituents Map of station ID to list of harmonic constituents
 * @property subordinateOffsets Map of station ID to subordinate offset data
 */
class HarmonicCalculator(
    private val constituents: Map<String, List<HarmonicConstituent>>,
    private val subordinateOffsets: Map<String, SubordinateOffset> = emptyMap()
) {

    companion object {
        /**
         * Time step for numerical derivative calculation (in seconds).
         */
        private const val DERIVATIVE_TIME_STEP_SECONDS = 60.0

        /**
         * Convergence threshold for Newton's method (in feet/meters).
         */
        private const val NEWTON_CONVERGENCE_THRESHOLD = 0.001

        /**
         * Maximum iterations for Newton's method.
         */
        private const val NEWTON_MAX_ITERATIONS = 20

        /**
         * Reference epoch for time calculations (NOAA standard: 1983-01-01 00:00:00 UTC).
         */
        private val REFERENCE_EPOCH = Instant.parse("1983-01-01T00:00:00Z")
    }

    /**
     * Calculate the tide height at a specific time for a station.
     *
     * @param stationId Station identifier
     * @param time UTC time for calculation
     * @return Tide height in feet or meters (relative to MLLW datum)
     * @throws IllegalArgumentException if station has no constituents
     */
    fun calculateHeight(stationId: String, time: Instant): Double {
        val stationConstituents = constituents[stationId]
            ?: throw IllegalArgumentException("No constituents found for station $stationId")

        if (stationConstituents.isEmpty()) {
            throw IllegalArgumentException("Station $stationId has no constituents")
        }

        // Calculate hours since reference epoch
        val hoursSinceEpoch = Duration.between(REFERENCE_EPOCH, time).seconds / 3600.0

        // Sum all constituent contributions
        var height = 0.0

        for (constituent in stationConstituents) {
            val constituentDef = Constituents.getConstituent(constituent.constituentName)
                ?: continue // Skip unknown constituents

            // Get node factor and equilibrium argument
            val nodeFactor = AstronomicalCalculator.calculateNodeFactor(constituentDef, time)
            val equilibriumArg = AstronomicalCalculator.calculateEquilibriumArgument(constituentDef, time)

            // Calculate constituent contribution
            // h = A × f × cos(ω × t + φ - κ)
            val omega = constituentDef.speed // degrees per hour
            val phase = constituent.phaseLocal // degrees
            val amplitude = constituent.amplitude // feet or meters

            val argument = toRadians(omega * hoursSinceEpoch + phase - equilibriumArg)
            val contribution = amplitude * nodeFactor * cos(argument)

            height += contribution
        }

        return height
    }

    /**
     * Calculate the rate of change of tide height (derivative) at a specific time.
     *
     * Uses numerical differentiation with a small time step.
     *
     * @param stationId Station identifier
     * @param time UTC time for calculation
     * @return Rate of change in feet/hour or meters/hour (positive = rising, negative = falling)
     */
    fun calculateRateOfChange(stationId: String, time: Instant): Double {
        val timeBefore = time.minusSeconds(DERIVATIVE_TIME_STEP_SECONDS.toLong())
        val timeAfter = time.plusSeconds(DERIVATIVE_TIME_STEP_SECONDS.toLong())

        val heightBefore = calculateHeight(stationId, timeBefore)
        val heightAfter = calculateHeight(stationId, timeAfter)

        val deltaHeight = heightAfter - heightBefore
        val deltaHours = (2.0 * DERIVATIVE_TIME_STEP_SECONDS) / 3600.0

        return deltaHeight / deltaHours
    }

    /**
     * Calculate current tide height with direction information.
     *
     * @param stationId Station identifier
     * @param time UTC time for calculation
     * @return TideHeight object with height, rate, and direction
     */
    fun calculateTideHeight(stationId: String, time: Instant): TideHeight {
        val height = calculateHeight(stationId, time)
        val rate = calculateRateOfChange(stationId, time)

        val direction = when {
            abs(rate) < TideHeight.SLACK_THRESHOLD -> TideHeight.Direction.SLACK
            rate > 0 -> TideHeight.Direction.RISING
            else -> TideHeight.Direction.FALLING
        }

        return TideHeight(
            time = time,
            height = height,
            rateOfChange = rate,
            direction = direction
        )
    }

    /**
     * Find the next tide extremum (high or low) after a given time.
     *
     * Uses Newton's method to find where the derivative (rate of change) equals zero.
     *
     * @param stationId Station identifier
     * @param startTime UTC time to start searching from
     * @param findHigh If true, find next high tide; if false, find next low tide
     * @return TideExtremum object with time and height, or null if not found
     */
    fun findNextExtremum(
        stationId: String,
        startTime: Instant,
        findHigh: Boolean
    ): TideExtremum? {
        // Start searching from slightly after startTime
        var searchTime = startTime.plusSeconds(600) // Start 10 minutes ahead

        // Typical tidal period is ~12.42 hours (semidiurnal) or ~24 hours (diurnal)
        // Search up to 30 hours to ensure we find at least one extremum
        val maxSearchTime = startTime.plusSeconds(30 * 3600)

        // Find the general vicinity of an extremum by looking for sign change in derivative
        var lastRate = calculateRateOfChange(stationId, searchTime)
        var searchStep = Duration.ofMinutes(30)

        while (searchTime.isBefore(maxSearchTime)) {
            searchTime = searchTime.plus(searchStep)
            val currentRate = calculateRateOfChange(stationId, searchTime)

            // Check for sign change (derivative crosses zero)
            if (lastRate * currentRate <= 0) {
                // Refine using Newton's method
                val extremum = refineExtremumWithNewton(
                    stationId,
                    searchTime.minusSeconds(searchStep.seconds),
                    searchTime
                )

                if (extremum != null) {
                    // Verify this is the type of extremum we're looking for
                    val isHigh = extremum.type == TideExtremum.Type.HIGH
                    if (isHigh == findHigh) {
                        return extremum
                    }
                }
            }

            lastRate = currentRate
        }

        return null // No extremum found
    }

    /**
     * Refine an extremum location using Newton's method.
     *
     * Newton's method: x_new = x_old - f'(x_old) / f''(x_old)
     * We're finding where f'(x) = 0 (rate of change = 0)
     *
     * @param stationId Station identifier
     * @param startTime Lower bound of search interval
     * @param endTime Upper bound of search interval
     * @return Refined TideExtremum, or null if convergence fails
     */
    private fun refineExtremumWithNewton(
        stationId: String,
        startTime: Instant,
        endTime: Instant
    ): TideExtremum? {
        var currentTime = Instant.ofEpochSecond(
            (startTime.epochSecond + endTime.epochSecond) / 2
        )

        for (iteration in 0 until NEWTON_MAX_ITERATIONS) {
            val rate = calculateRateOfChange(stationId, currentTime)

            // Check convergence
            if (abs(rate) < NEWTON_CONVERGENCE_THRESHOLD) {
                val height = calculateHeight(stationId, currentTime)

                // Determine if this is a high or low tide by checking second derivative
                val rateBefore = calculateRateOfChange(
                    stationId,
                    currentTime.minusSeconds(300)
                )
                val rateAfter = calculateRateOfChange(
                    stationId,
                    currentTime.plusSeconds(300)
                )
                val secondDerivative = (rateAfter - rateBefore) / (10.0 / 60.0) // per 10 minutes

                val type = if (secondDerivative < 0) {
                    TideExtremum.Type.HIGH
                } else {
                    TideExtremum.Type.LOW
                }

                return TideExtremum(
                    time = currentTime,
                    height = height,
                    type = type
                )
            }

            // Calculate second derivative (change in rate)
            val timeDelta = 300L // 5 minutes
            val rateBefore = calculateRateOfChange(
                stationId,
                currentTime.minusSeconds(timeDelta)
            )
            val rateAfter = calculateRateOfChange(
                stationId,
                currentTime.plusSeconds(timeDelta)
            )
            val secondDerivative = (rateAfter - rateBefore) / (2.0 * timeDelta / 3600.0)

            if (abs(secondDerivative) < 0.0001) {
                // Second derivative too small, cannot converge
                break
            }

            // Newton's method step
            val timeAdjustmentHours = -rate / secondDerivative
            val timeAdjustmentSeconds = (timeAdjustmentHours * 3600.0).toLong()

            currentTime = currentTime.plusSeconds(timeAdjustmentSeconds)

            // Ensure we stay within bounds
            if (currentTime.isBefore(startTime.minusSeconds(3600)) ||
                currentTime.isAfter(endTime.plusSeconds(3600))) {
                break
            }
        }

        return null // Failed to converge
    }

    /**
     * Find all extrema (highs and lows) within a time range.
     *
     * @param stationId Station identifier
     * @param startTime Start of time range (UTC)
     * @param endTime End of time range (UTC)
     * @return List of TideExtremum objects sorted by time
     */
    fun findExtrema(
        stationId: String,
        startTime: Instant,
        endTime: Instant
    ): List<TideExtremum> {
        val extrema = mutableListOf<TideExtremum>()
        var currentTime = startTime

        // Alternate between searching for highs and lows
        // Start by determining what we're looking for based on current rate
        var findHigh = calculateRateOfChange(stationId, currentTime) > 0

        while (currentTime.isBefore(endTime)) {
            val extremum = findNextExtremum(stationId, currentTime, findHigh)
                ?: break

            if (extremum.time.isAfter(endTime)) {
                break
            }

            extrema.add(extremum)
            currentTime = extremum.time
            findHigh = !findHigh // Alternate
        }

        return extrema.sortedBy { it.time }
    }

    /**
     * Generate a tide curve (height at regular intervals) over a time range.
     *
     * @param stationId Station identifier
     * @param startTime Start of time range (UTC)
     * @param endTime End of time range (UTC)
     * @param intervalMinutes Time interval between points in minutes
     * @return List of TideHeight objects
     */
    fun generateTideCurve(
        stationId: String,
        startTime: Instant,
        endTime: Instant,
        intervalMinutes: Int = 10
    ): List<TideHeight> {
        val curve = mutableListOf<TideHeight>()
        var currentTime = startTime

        while (currentTime.isBefore(endTime) || currentTime == endTime) {
            curve.add(calculateTideHeight(stationId, currentTime))
            currentTime = currentTime.plusSeconds(intervalMinutes * 60L)
        }

        return curve
    }

    /**
     * Convert degrees to radians.
     */
    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0
}
