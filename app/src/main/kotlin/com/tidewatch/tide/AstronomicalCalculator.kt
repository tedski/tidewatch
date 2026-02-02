package com.tidewatch.tide

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.*

/**
 * Calculates astronomical factors for harmonic tide analysis.
 *
 * Computes node factors (f) and equilibrium arguments (V+u = κ) for each
 * tidal constituent based on the positions of the moon, sun, and planets.
 *
 * Based on:
 * - Schureman, P. (1958). Manual of Harmonic Analysis and Prediction of Tides.
 * - NOAA Special Publication No. 98 (revised 1940, reprinted 1958 with corrections)
 *
 * Implementation follows the standard approach used by NOAA and international agencies.
 */
object AstronomicalCalculator {

    /**
     * Calculate the node factor (f) for a constituent at a given time.
     *
     * The node factor accounts for the 18.6-year cycle of the moon's orbital plane
     * and other long-period variations.
     *
     * @param constituent The tidal constituent
     * @param time UTC time for which to calculate
     * @return Node factor (typically 0.8 to 1.2)
     */
    fun calculateNodeFactor(constituent: Constituents.Constituent, time: Instant): Double {
        val astronomicalArgs = getAstronomicalArguments(time)

        return when (constituent.name) {
            // Principal semidiurnal
            "M2" -> 1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))
            "S2" -> 1.0 // Solar constituents have no lunar node factor
            "N2" -> 1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))
            "K2" -> 1.024 + 0.286 * cos(astronomicalArgs.N) + 0.008 * cos(2.0 * astronomicalArgs.N)

            // Principal diurnal
            "K1" -> 1.006 + 0.115 * cos(astronomicalArgs.N) - 0.009 * cos(2.0 * astronomicalArgs.N)
            "O1" -> 1.009 + 0.187 * cos(astronomicalArgs.N) - 0.015 * cos(2.0 * astronomicalArgs.N)
            "P1" -> 1.0 // Solar
            "Q1" -> 1.009 + 0.187 * cos(astronomicalArgs.N) - 0.015 * cos(2.0 * astronomicalArgs.N)

            // Long period
            "Mm" -> 1.0 - 0.130 * cos(astronomicalArgs.N)
            "Mf" -> 1.043 + 0.414 * cos(astronomicalArgs.N)
            "Ssa", "Sa" -> 1.0 // Solar

            // Additional semidiurnal
            "2N2" -> 1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))
            "μ2" -> 1.0
            "ν2" -> 1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))
            "λ2" -> 1.0
            "L2" -> 1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))
            "T2", "R2" -> 1.0 // Solar

            // Additional diurnal
            "2Q1" -> 1.009 + 0.187 * cos(astronomicalArgs.N) - 0.015 * cos(2.0 * astronomicalArgs.N)
            "σ1" -> 1.009 + 0.187 * cos(astronomicalArgs.N) - 0.015 * cos(2.0 * astronomicalArgs.N)
            "ρ1" -> 1.009 + 0.187 * cos(astronomicalArgs.N) - 0.015 * cos(2.0 * astronomicalArgs.N)
            "M1" -> 1.043 + 0.414 * cos(astronomicalArgs.N)
            "J1" -> 1.006 + 0.115 * cos(astronomicalArgs.N) - 0.009 * cos(2.0 * astronomicalArgs.N)
            "OO1" -> 1.009 + 0.187 * cos(astronomicalArgs.N) - 0.015 * cos(2.0 * astronomicalArgs.N)

            // Shallow water constituents (compound)
            "M4" -> (1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))).pow(2.0)
            "MS4" -> (1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))) * 1.0
            "M6" -> (1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))).pow(3.0)
            "M8" -> (1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))).pow(4.0)
            "MK3" -> (1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))) *
                     (1.006 + 0.115 * cos(astronomicalArgs.N) - 0.009 * cos(2.0 * astronomicalArgs.N))
            "S4", "S6" -> 1.0 // Solar
            "MN4" -> (1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))) *
                     (1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi)))
            "2SM2" -> 1.0
            "2MK3" -> (1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))).pow(2.0) *
                      (1.006 + 0.115 * cos(astronomicalArgs.N) - 0.009 * cos(2.0 * astronomicalArgs.N))
            "MSf" -> 1.0
            "MO3" -> (1.0 - 0.037 * cos(2.0 * (astronomicalArgs.N - astronomicalArgs.xi))).pow(2.0)
            "S1" -> 1.0 // Solar

            else -> 1.0 // Default for unknown constituents
        }
    }

    /**
     * Calculate the equilibrium argument (V + u) in degrees for a constituent.
     *
     * This is the astronomical phase correction that accounts for the changing
     * positions of celestial bodies.
     *
     * @param constituent The tidal constituent
     * @param time UTC time for which to calculate
     * @return Equilibrium argument in degrees (0-360)
     */
    fun calculateEquilibriumArgument(constituent: Constituents.Constituent, time: Instant): Double {
        val args = getAstronomicalArguments(time)
        val doodson = constituent.doodsonNumbers

        // Calculate V + u using Doodson numbers
        // Formula: V + u = τ*T + s*s + h*h + p*p + N'*N + p'*p'
        val vPlusU = doodson[0] * args.T +
                     doodson[1] * args.s +
                     doodson[2] * args.h +
                     doodson[3] * args.p +
                     doodson[4] * args.N +
                     doodson[5] * args.p1

        // Normalize to 0-360 degrees
        return normalizeAngle(vPlusU)
    }

    /**
     * Astronomical arguments (angles) in degrees.
     */
    private data class AstronomicalArguments(
        val T: Double,   // Mean lunar time (hour angle of mean moon)
        val s: Double,   // Mean longitude of moon
        val h: Double,   // Mean longitude of sun
        val p: Double,   // Longitude of moon's perigee
        val N: Double,   // Negative longitude of moon's ascending node
        val p1: Double,  // Longitude of sun's perigee
        val xi: Double   // Obliquity factor
    )

    /**
     * Calculate fundamental astronomical arguments for a given time.
     *
     * Based on Schureman (1958) formulas, updated for modern precision.
     *
     * @param time UTC time
     * @return Astronomical arguments in degrees
     */
    private fun getAstronomicalArguments(time: Instant): AstronomicalArguments {
        // Calculate time in Julian centuries from J2000.0
        val j2000 = Instant.parse("2000-01-01T12:00:00Z")
        val daysSinceJ2000 = ChronoUnit.DAYS.between(j2000, time).toDouble() +
                             time.epochSecond % 86400 / 86400.0
        val T = daysSinceJ2000 / 36525.0 // Julian centuries

        // Mean lunar time (hour angle) - degrees
        val tau = 15.0 * (time.atZone(ZoneOffset.UTC).hour +
                         time.atZone(ZoneOffset.UTC).minute / 60.0 +
                         time.atZone(ZoneOffset.UTC).second / 3600.0)

        // Mean longitude of moon - degrees
        val s = 218.3164477 + 481267.88123421 * T -
                0.0015786 * T * T +
                T * T * T / 538841.0 -
                T * T * T * T / 65194000.0

        // Mean longitude of sun - degrees
        val h = 280.46646 + 36000.76983 * T + 0.0003032 * T * T

        // Longitude of moon's perigee - degrees
        val p = 83.3532465 + 4069.0137287 * T -
                0.0103200 * T * T -
                T * T * T / 80053.0 +
                T * T * T * T / 18999000.0

        // Negative longitude of moon's ascending node - degrees
        val N = 125.04452 - 1934.136261 * T +
                0.0020708 * T * T +
                T * T * T / 450000.0

        // Longitude of sun's perigee - degrees
        val p1 = 282.93735 + 1.71946 * T + 0.00046 * T * T

        // Obliquity factor
        val xi = N

        return AstronomicalArguments(
            T = normalizeAngle(tau),
            s = normalizeAngle(s),
            h = normalizeAngle(h),
            p = normalizeAngle(p),
            N = toRadians(normalizeAngle(N)),
            p1 = normalizeAngle(p1),
            xi = toRadians(normalizeAngle(xi))
        )
    }

    /**
     * Normalize an angle to the range [0, 360) degrees.
     */
    private fun normalizeAngle(degrees: Double): Double {
        var normalized = degrees % 360.0
        if (normalized < 0.0) {
            normalized += 360.0
        }
        return normalized
    }

    /**
     * Convert degrees to radians.
     */
    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0
}
