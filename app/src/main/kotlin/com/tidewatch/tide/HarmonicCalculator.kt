package com.tidewatch.tide

import com.tidewatch.data.models.*
import java.time.Duration
import java.time.Instant
import kotlin.math.*

/**
 * Core harmonic analysis engine for tide prediction.
 *
 * Implements the standard harmonic method used by NOAA:
 * h(t) = Z₀ + Σ[ f_i(t) × A_i × cos(ω_i × (t-t₀) + V₀_i + u_i(t) - κ_i) ]
 *
 * Where:
 * - Z₀ = datum offset (MSL above MLLW in feet)
 * - A_i = constituent amplitude (from database)
 * - f_i(t) = node factor at prediction time (from astronomical calculation)
 * - ω_i = angular velocity (constituent speed in degrees/hour)
 * - V₀_i = equilibrium argument V at reference epoch (from Doodson numbers)
 * - u_i(t) = nodal phase correction at prediction time (Schureman formulas)
 * - κ_i = phase_GMT from NOAA database
 *
 * Supports subordinate stations by transparently resolving to reference stations
 * and applying time/height offsets.
 *
 * @property constituents Map of station ID to list of harmonic constituents
 * @property subordinateOffsets Map of station ID to subordinate offset data
 * @property datumOffsets Map of station ID to Z₀ datum offset (MSL above MLLW)
 */
class HarmonicCalculator(
    private val constituents: Map<String, List<HarmonicConstituent>>,
    subordinateOffsets: Map<String, SubordinateOffset> = emptyMap(),
    private val datumOffsets: Map<String, Double> = emptyMap()
) {
    /**
     * Helper for subordinate station calculations.
     */
    private val subordinateCalculator = SubordinateCalculator(subordinateOffsets)

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
     * Pre-computed V0 (equilibrium argument at reference epoch) for each constituent.
     * Calculated once in constructor and reused to avoid midnight discontinuities.
     * Immutable and thread-safe.
     */
    private val v0Cache: Map<String, Double> = Constituents.ALL.associate { constituent ->
        constituent.name to AstronomicalCalculator.calculateEquilibriumArgument(
            constituent,
            REFERENCE_EPOCH
        )
    }

    /**
     * Calculate the tide height at a specific time for a station.
     *
     * For subordinate stations, automatically resolves to reference station,
     * calculates using reference harmonics, and applies height offsets.
     *
     * @param stationId Station identifier (harmonic or subordinate)
     * @param time UTC time for calculation
     * @return Tide height in feet or meters (relative to MLLW datum)
     * @throws IllegalArgumentException if station has no constituents and is not subordinate
     */
    fun calculateHeight(stationId: String, time: Instant): Double {
        // Resolve to reference station if subordinate
        val effectiveStationId = subordinateCalculator.getReferenceStationId(stationId)
            ?: stationId

        val stationConstituents = constituents[effectiveStationId]
            ?: throw IllegalArgumentException("No constituents found for station $stationId")

        if (stationConstituents.isEmpty()) {
            throw IllegalArgumentException("Station $stationId has no constituents")
        }

        // Calculate hours since reference epoch
        val hoursSinceEpoch = Duration.between(REFERENCE_EPOCH, time).seconds / 3600.0

        // Start with datum offset Z₀ (MSL above MLLW)
        var height = datumOffsets[effectiveStationId] ?: 0.0

        for (constituent in stationConstituents) {
            val constituentDef = Constituents.getConstituent(constituent.constituentName)
                ?: continue // Skip unknown constituents

            // Get pre-computed V0 (equilibrium argument at reference epoch) from immutable cache
            // This should never be null since we pre-compute all constituents
            val v0 = v0Cache[constituent.constituentName]
                ?: error("V0 not found for constituent ${constituent.constituentName}")

            // Get nodal corrections for current time
            val nodeFactor = AstronomicalCalculator.calculateNodeFactor(constituentDef, time)
            val nodalPhase = AstronomicalCalculator.calculateNodalPhase(constituentDef, time)

            // NOAA prediction formula:
            // h = f(t) × A × cos(ω × (t-t₀) + V₀ + u(t) - κ)
            val omega = constituentDef.speed // degrees per hour
            val phase = constituent.phaseLocal // phase_GMT (κ) in degrees
            val amplitude = constituent.amplitude // feet or meters

            val argument = toRadians(omega * hoursSinceEpoch + (v0 + nodalPhase) - phase)
            val contribution = amplitude * nodeFactor * cos(argument)

            height += contribution
        }

        // Apply subordinate station height offset if needed
        if (subordinateCalculator.isSubordinateStation(stationId)) {
            val referenceRate = calculateRateOfChangeInternal(effectiveStationId, time)
            height = subordinateCalculator.applyHeightOffset(stationId, height, referenceRate)
        }

        return height
    }

    /**
     * Calculate the rate of change of tide height (derivative) at a specific time.
     *
     * Uses numerical differentiation with a small time step.
     * For subordinate stations, returns the reference station's rate (approximation).
     *
     * @param stationId Station identifier
     * @param time UTC time for calculation
     * @return Rate of change in feet/hour or meters/hour (positive = rising, negative = falling)
     */
    fun calculateRateOfChange(stationId: String, time: Instant): Double {
        // Use reference station for rate calculation to avoid circular dependency
        // (we need rate to determine height multiplier for subordinates)
        val effectiveStationId = subordinateCalculator.getReferenceStationId(stationId)
            ?: stationId

        return calculateRateOfChangeInternal(effectiveStationId, time)
    }

    /**
     * Internal rate of change calculation without subordinate resolution.
     * Used to avoid circular dependency when applying subordinate offsets.
     */
    private fun calculateRateOfChangeInternal(stationId: String, time: Instant): Double {
        val timeBefore = time.minusSeconds(DERIVATIVE_TIME_STEP_SECONDS.toLong())
        val timeAfter = time.plusSeconds(DERIVATIVE_TIME_STEP_SECONDS.toLong())

        // Calculate reference station heights at both times
        val effectiveStationId = subordinateCalculator.getReferenceStationId(stationId)
            ?: stationId

        val heightBefore = calculateHeightInternal(effectiveStationId, timeBefore)
        val heightAfter = calculateHeightInternal(effectiveStationId, timeAfter)

        val deltaHeight = heightAfter - heightBefore
        val deltaHours = (2.0 * DERIVATIVE_TIME_STEP_SECONDS) / 3600.0

        return deltaHeight / deltaHours
    }

    /**
     * Internal height calculation without subordinate offsets.
     * Used to calculate reference station heights for rate calculations.
     */
    private fun calculateHeightInternal(stationId: String, time: Instant): Double {
        val stationConstituents = constituents[stationId]
            ?: throw IllegalArgumentException("No constituents found for station $stationId")

        if (stationConstituents.isEmpty()) {
            throw IllegalArgumentException("Station $stationId has no constituents")
        }

        // Calculate hours since reference epoch
        val hoursSinceEpoch = Duration.between(REFERENCE_EPOCH, time).seconds / 3600.0

        // Start with datum offset Z₀ (MSL above MLLW)
        var height = datumOffsets[stationId] ?: 0.0

        for (constituent in stationConstituents) {
            val constituentDef = Constituents.getConstituent(constituent.constituentName)
                ?: continue // Skip unknown constituents

            val v0 = v0Cache[constituent.constituentName]
                ?: error("V0 not found for constituent ${constituent.constituentName}")

            val nodeFactor = AstronomicalCalculator.calculateNodeFactor(constituentDef, time)
            val nodalPhase = AstronomicalCalculator.calculateNodalPhase(constituentDef, time)

            val omega = constituentDef.speed
            val phase = constituent.phaseLocal // phase_GMT (κ)
            val amplitude = constituent.amplitude

            val argument = toRadians(omega * hoursSinceEpoch + (v0 + nodalPhase) - phase)
            val contribution = amplitude * nodeFactor * cos(argument)

            height += contribution
        }

        return height
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
     * For subordinate stations, finds extremum at reference station, applies time offset,
     * and recalculates height at offset time with appropriate multiplier.
     *
     * @param stationId Station identifier (harmonic or subordinate)
     * @param startTime UTC time to start searching from
     * @param findHigh If true, find next high tide; if false, find next low tide
     * @return TideExtremum object with time and height, or null if not found
     */
    fun findNextExtremum(
        stationId: String,
        startTime: Instant,
        findHigh: Boolean
    ): TideExtremum? {
        // For subordinate stations, work with reference station
        val effectiveStationId = subordinateCalculator.getReferenceStationId(stationId)
            ?: stationId

        // Start searching from slightly after startTime
        var searchTime = startTime.plusSeconds(600) // Start 10 minutes ahead

        // Typical tidal period is ~12.42 hours (semidiurnal) or ~24 hours (diurnal)
        // Search up to 30 hours to ensure we find at least one extremum
        val maxSearchTime = startTime.plusSeconds(30 * 3600)

        // Find the general vicinity of an extremum by looking for sign change in derivative
        var lastRate = calculateRateOfChange(effectiveStationId, searchTime)
        var searchStep = Duration.ofMinutes(30)

        while (searchTime.isBefore(maxSearchTime)) {
            searchTime = searchTime.plus(searchStep)
            val currentRate = calculateRateOfChange(effectiveStationId, searchTime)

            // Check for sign change (derivative crosses zero)
            if (lastRate * currentRate <= 0) {
                // Refine using Newton's method on reference station
                val extremum = refineExtremumWithNewton(
                    effectiveStationId,
                    searchTime.minusSeconds(searchStep.seconds),
                    searchTime
                )

                if (extremum != null) {
                    // Verify this is the type of extremum we're looking for
                    val isHigh = extremum.type == TideExtremum.Type.HIGH
                    if (isHigh == findHigh) {
                        // Apply subordinate station time offset if needed
                        if (subordinateCalculator.isSubordinateStation(stationId)) {
                            val offsetTime = subordinateCalculator.applyTimeOffset(
                                stationId,
                                extremum.type,
                                extremum.time
                            )

                            // Recalculate height at offset time with subordinate multiplier
                            val offsetHeight = calculateHeight(stationId, offsetTime)

                            return TideExtremum(
                                time = offsetTime,
                                height = offsetHeight,
                                type = extremum.type
                            )
                        }

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
