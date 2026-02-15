package com.tidewatch.tide

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.*

/**
 * Calculates astronomical factors for harmonic tide analysis.
 *
 * Computes node factors (f), nodal phase corrections (u), and equilibrium
 * arguments (V) for each tidal constituent based on the positions of the
 * moon, sun, and planets.
 *
 * Based on:
 * - Schureman, P. (1958). Manual of Harmonic Analysis and Prediction of Tides.
 *   NOAA Special Publication No. 98 (revised 1940, reprinted 1958 with corrections)
 * - NOAA tide_fac.f Fortran implementation (37 constituents)
 * - XTide (https://flaterco.com/xtide/) reference implementation
 *
 * The prediction formula is:
 *   h(t) = Z₀ + Σ[ f(t) × H × cos(ω×(t-t₀) + V₀ + u(t) - κ) ]
 *
 * Where:
 * - V₀ = equilibrium argument V at reference epoch t₀ (from Doodson numbers)
 * - u(t) = nodal phase correction at prediction time (from this calculator)
 * - f(t) = node factor at prediction time (from this calculator)
 * - κ = phase_GMT from NOAA database
 */
object AstronomicalCalculator {

    /**
     * J2000.0 epoch: 2000-01-01 12:00:00 UTC (noon, not midnight).
     * Used as reference for astronomical calculations.
     */
    private val J2000_EPOCH = Instant.parse("2000-01-01T12:00:00Z")

    /**
     * Schureman orbital parameters derived from the lunar node longitude N.
     * These intermediate values are needed for node factor and nodal phase calculations.
     *
     * @property I Mean inclination of lunar orbit to celestial equator (degrees)
     * @property nu Right ascension correction of lunar intersection (degrees)
     * @property xi Longitude in moon's orbit of lunar intersection (degrees)
     * @property nup Higher-order correction for K1 (degrees)
     * @property nupp Higher-order correction for K2 (degrees)
     * @property P Longitude of lunar perigee referred to equinox (degrees)
     */
    private data class OrbitalParameters(
        val I: Double,
        val nu: Double,
        val xi: Double,
        val nup: Double,
        val nupp: Double,
        val P: Double
    )

    /**
     * Calculate Schureman orbital parameters from the lunar node longitude.
     *
     * These are the intermediate parameters needed for computing node factors (f)
     * and nodal phase corrections (u). Formulas from Schureman (1958) and the
     * NOAA tide_fac.f Fortran implementation.
     *
     * @param N Longitude of moon's ascending node in degrees
     * @param p Longitude of moon's perigee in degrees
     * @return Orbital parameters
     */
    private fun computeOrbitalParameters(N: Double, p: Double): OrbitalParameters {
        val N_rad = toRadians(N)

        // I = inclination of lunar orbit to celestial equator
        // Schureman formula: I = acos(0.9136949 - 0.0356926 * cos(N))
        val cosI = 0.9136949 - 0.0356926 * cos(N_rad)
        val I = Math.toDegrees(acos(cosI.coerceIn(-1.0, 1.0)))
        val I_rad = toRadians(I)

        // nu = right ascension of lunar intersection
        // Schureman formula: nu = asin(0.0897056 * sin(N) / sin(I))
        val sinNu = (0.0897056 * sin(N_rad) / sin(I_rad)).coerceIn(-1.0, 1.0)
        val nu = Math.toDegrees(asin(sinNu))

        // xi = longitude in moon's orbit of lunar intersection
        // Schureman formula: xi = N - 2*atan(0.64412*tan(N/2)) - nu
        val xi = N - Math.toDegrees(2.0 * atan(0.64412 * tan(N_rad / 2.0))) - nu

        // nup (nu') = higher-order correction used for K1
        // Schureman formula 224
        val sinNup = (sin(toRadians(nu)) / (cos(toRadians(nu)) + 0.334766 / sin(2.0 * I_rad)))
        val nup = Math.toDegrees(atan(sinNup))

        // nupp (nu'') = higher-order correction used for K2
        // Schureman formula 232
        val sin2Nu = sin(2.0 * toRadians(nu))
        val cos2Nu = cos(2.0 * toRadians(nu))
        val sinI = sin(I_rad)
        val sinNupp = sin2Nu / (cos2Nu + 0.0726184 / (sinI * sinI))
        val nupp = Math.toDegrees(atan(sinNupp)) / 2.0

        // P = longitude of lunar perigee reckoned from lunar intersection
        // Used for M1 and L2 nodal corrections
        val P = p - xi

        return OrbitalParameters(I, nu, xi, nup, nupp, P)
    }

    /**
     * Calculate the node factor (f) for a constituent at a given time.
     *
     * The node factor accounts for the 18.6-year cycle of the moon's orbital plane
     * and other long-period variations. It modulates the constituent amplitude.
     *
     * Formulas from Schureman (1958) and NOAA tide_fac.f:
     * - f_M2 uses Schureman Eq. 78
     * - f_O1 uses Schureman Eq. 75
     * - f_K1 uses Schureman Eq. 227
     * - f_K2 uses Schureman Eq. 235
     *
     * @param constituent The tidal constituent
     * @param time UTC time for which to calculate
     * @return Node factor (typically 0.8 to 1.2)
     */
    fun calculateNodeFactor(constituent: Constituents.Constituent, time: Instant): Double {
        val args = getAstronomicalArguments(time)
        val orb = computeOrbitalParameters(args.N, args.p)

        val I_rad = toRadians(orb.I)
        val P_rad = toRadians(orb.P)

        // Schureman equation numbers referenced in comments

        // Eq. 78: f(M2) = (cos(I/2))^4 / 0.91544
        val eq78 = cos(I_rad / 2.0).pow(4.0) / 0.91544

        // Eq. 149: f(Mm) (approximate)
        val eq149 = (2.0/3.0 - sin(I_rad).pow(2.0)) / 0.5021

        // Eq. 207: f(Mf) = sin(I)^2 / 0.1578
        val eq207 = sin(I_rad).pow(2.0) / 0.1578

        // Eq. 75: f(O1) = sin(I) * cos(I/2)^2 / 0.37689
        val eq75 = sin(I_rad) * cos(I_rad / 2.0).pow(2.0) / 0.37689

        // Eq. 227: f(K1) = sqrt(0.8965*sin(2I)^2 + 0.6001*sin(2I)*cos(nu) + 0.1006)
        val sin2I = sin(2.0 * I_rad)
        val cosNu = cos(toRadians(orb.nu))
        val eq227 = sqrt(0.8965 * sin2I.pow(2.0) + 0.6001 * sin2I * cosNu + 0.1006)

        // Eq. 215: f(J1) = sin(2I) / 0.7214
        val eq215 = sin(2.0 * I_rad) / 0.7214

        // Eq. 235: f(K2) = sqrt(19.0444*sin(I)^4 + 2.7702*sin(I)^2*cos(2*nu) + 0.0981)
        val sinI = sin(I_rad)
        val cos2Nu = cos(2.0 * toRadians(orb.nu))
        val eq235 = sqrt(19.0444 * sinI.pow(4.0) + 2.7702 * sinI.pow(2.0) * cos2Nu + 0.0981)

        // Eq. 206: f(OO1) = sin(I) * sin(I/2)^2 / 0.01640
        val eq206 = sinI * sin(I_rad / 2.0).pow(2.0) / 0.01640

        // Eq. 215 for L2 with R correction (Schureman 215)
        // For L2, we need the R factor
        val tanHalfI = tan(I_rad / 2.0)
        val cosP = cos(P_rad)
        val sinP = sin(P_rad)
        val sin2P = sin(2.0 * P_rad)
        val cos2P = cos(2.0 * P_rad)
        val oneOverSixTanSqInv = (1.0 / 6.0) * (1.0 / tanHalfI.pow(2.0))
        val Ra = sqrt(1.0 - 12.0 * tanHalfI.pow(2.0) * cos2P + 36.0 * tanHalfI.pow(4.0))
        val eq215L2 = eq78 * Ra

        // Eq. 202 for M1
        val q2Denom = 7.0 * cos(I_rad) + 1.0
        val q2Num = (5.0 * cos(I_rad) - 1.0)
        val Q = Math.toDegrees(atan(q2Num / q2Denom * tan(P_rad)))
        val eq202f = sin(2.0 * I_rad) / sin(I_rad) *
                     (1.0 / sqrt(0.25 + q2Num.pow(2.0) / q2Denom.pow(2.0) * sin(P_rad).pow(2.0)))

        return when (constituent.name) {
            // Principal semidiurnal
            "M2" -> eq78
            "S2" -> 1.0
            "N2" -> eq78
            "K2" -> eq235

            // Principal diurnal
            "K1" -> eq227
            "O1" -> eq75
            "P1" -> 1.0
            "Q1" -> eq75

            // Long period
            "Mm" -> eq149
            "Mf" -> eq207
            "Ssa", "Sa" -> 1.0

            // Additional semidiurnal
            "2N2" -> eq78
            "μ2" -> eq78
            "ν2" -> eq78
            "λ2" -> eq78
            "L2" -> eq215L2
            "T2", "R2" -> 1.0

            // Additional diurnal
            "2Q1" -> eq75
            "σ1" -> eq75
            "ρ1" -> eq75
            "M1" -> eq207  // Simplified; full M1 is more complex
            "J1" -> eq215
            "OO1" -> eq206

            // Shallow water constituents (compound)
            "M4" -> eq78.pow(2.0)
            "MS4" -> eq78
            "M6" -> eq78.pow(3.0)
            "M8" -> eq78.pow(4.0)
            "MK3" -> eq78 * eq227
            "S4", "S6" -> 1.0
            "MN4" -> eq78.pow(2.0)
            "2SM2" -> eq78
            "2MK3" -> eq78.pow(2.0) * eq227
            "MSf" -> eq207
            "MO3" -> eq78 * eq75
            "S1" -> 1.0

            else -> 1.0
        }
    }

    /**
     * Calculate the nodal phase correction (u) in degrees for a constituent.
     *
     * The nodal phase correction u accounts for the 18.6-year nodal cycle's
     * effect on the PHASE of each constituent. This is separate from the
     * node factor f which affects AMPLITUDE.
     *
     * u varies slowly (period ~18.6 years) and must be evaluated at the
     * prediction time, not the reference epoch.
     *
     * Formulas from Schureman (1958) and NOAA tide_fac.f.
     *
     * @param constituent The tidal constituent
     * @param time UTC time for which to calculate
     * @return Nodal phase correction in degrees
     */
    fun calculateNodalPhase(constituent: Constituents.Constituent, time: Instant): Double {
        val args = getAstronomicalArguments(time)
        val orb = computeOrbitalParameters(args.N, args.p)

        val I_rad = toRadians(orb.I)
        val P_rad = toRadians(orb.P)

        // u for M2 = 2*(xi - nu) -- Schureman
        val u_M2 = 2.0 * (orb.xi - orb.nu)

        // u for O1 = 2*xi - nu -- Schureman
        val u_O1 = 2.0 * orb.xi - orb.nu

        // u for K1 = -nup -- Schureman Eq. 224
        val u_K1 = -orb.nup

        // u for K2 = -2*nupp -- Schureman Eq. 232
        val u_K2 = -2.0 * orb.nupp

        // u for J1 = -nu -- Schureman
        val u_J1 = -orb.nu

        // u for Mf = -2*xi -- Schureman
        val u_Mf = -2.0 * orb.xi

        // u for OO1 = -2*xi - nu -- Schureman
        val u_OO1 = -2.0 * orb.xi - orb.nu

        // u for M1 = xi - nu + Q (Schureman Eq. 202)
        val q2Denom = 7.0 * cos(I_rad) + 1.0
        val q2Num = 5.0 * cos(I_rad) - 1.0
        val Q = Math.toDegrees(atan(q2Num / q2Denom * tan(P_rad)))
        val u_M1 = orb.xi - orb.nu + Q

        // u for L2 = 2*xi - 2*nu - R (Schureman Eq. 214)
        val tanHalfI = tan(I_rad / 2.0)
        val sin2P = sin(2.0 * P_rad)
        val cos2P = cos(2.0 * P_rad)
        val R = Math.toDegrees(atan(sin2P / ((1.0 / 6.0) / tanHalfI.pow(2.0) - cos2P)))
        val u_L2 = 2.0 * orb.xi - 2.0 * orb.nu - R

        return when (constituent.name) {
            // Principal semidiurnal
            "M2" -> u_M2
            "S2" -> 0.0
            "N2" -> u_M2
            "K2" -> u_K2

            // Principal diurnal
            "K1" -> u_K1
            "O1" -> u_O1
            "P1" -> 0.0
            "Q1" -> u_O1

            // Long period
            "Mm" -> 0.0
            "Mf" -> u_Mf
            "Ssa", "Sa" -> 0.0

            // Additional semidiurnal
            "2N2" -> u_M2
            "μ2" -> u_M2
            "ν2" -> u_M2
            "λ2" -> u_M2
            "L2" -> u_L2
            "T2", "R2" -> 0.0

            // Additional diurnal
            "2Q1" -> u_O1
            "σ1" -> u_O1
            "ρ1" -> u_O1
            "M1" -> u_M1
            "J1" -> u_J1
            "OO1" -> u_OO1

            // Shallow water constituents (compound)
            // u for compound constituents is the sum of u for component constituents
            "M4" -> 2.0 * u_M2
            "MS4" -> u_M2         // M2 + S2, and u(S2) = 0
            "M6" -> 3.0 * u_M2
            "M8" -> 4.0 * u_M2
            "MK3" -> u_M2 + u_K1  // M2 + K1
            "S4", "S6" -> 0.0
            "MN4" -> 2.0 * u_M2   // M2 + N2, both have same u
            "2SM2" -> -u_M2       // 2*S2 - M2
            "2MK3" -> 2.0 * u_M2 + u_K1  // 2*M2 + K1
            "MSf" -> 0.0          // (approximately)
            "MO3" -> u_M2 + u_O1  // M2 + O1
            "S1" -> 0.0

            else -> 0.0
        }
    }

    /**
     * Calculate the equilibrium argument (V only, without u) in degrees for a constituent.
     *
     * This computes the astronomical argument V at the given time using Doodson numbers.
     * The nodal correction u is NOT included here -- it is calculated separately by
     * calculateNodalPhase() and must be added at prediction time.
     *
     * The full phase in the prediction formula is: V₀ + u(t) - κ
     * where V₀ is evaluated at reference epoch and u(t) at prediction time.
     *
     * @param constituent The tidal constituent
     * @param time UTC time for which to calculate
     * @return Equilibrium argument V in degrees (0-360)
     */
    fun calculateEquilibriumArgument(constituent: Constituents.Constituent, time: Instant): Double {
        val args = getAstronomicalArguments(time)
        val doodson = constituent.doodsonNumbers

        // Calculate V using Doodson numbers with SP98-derived phase offset:
        // V = d₁×τ + d₂×s + d₃×h + d₄×p + d₅×N' + d₆×p₁ + phaseOffset
        //
        // The phaseOffset bridges between the Doodson tau convention (midnight epoch)
        // and the SP98 T convention (noon epoch) used by NOAA/XTide/congen.
        // For odd-order tau constituents (diurnal, terdiurnal), T mod 360 = 180
        // at midnight UTC while tau = 0, creating a d_tau*180 degree offset that
        // must be combined with SP98's constant "c" from Table 2:
        //   phaseOffset = c_SP98 + (d_tau mod 2) * 180  (mod 360)
        //
        // Without this, diurnal constituents (K1, O1, P1, Q1) would have ~90°
        // phase errors, causing 30-60 minute timing discrepancies.
        val v = doodson[0] * args.T +
                doodson[1] * args.s +
                doodson[2] * args.h +
                doodson[3] * args.p +
                doodson[4] * args.N +
                doodson[5] * args.p1 +
                constituent.phaseOffset

        // Normalize to 0-360 degrees
        return normalizeAngle(v)
    }

    /**
     * Astronomical arguments (angles) in degrees.
     * These are the fundamental variables used in Doodson's expansion of the tide-generating potential.
     */
    private data class AstronomicalArguments(
        val T: Double,   // Mean lunar time (tau = 15*UT + h - s) - NOT normalized for continuity
        val s: Double,   // Mean longitude of moon
        val h: Double,   // Mean longitude of sun
        val p: Double,   // Longitude of moon's perigee
        val N: Double,   // Longitude of moon's ascending node (NOT negated here; see note)
        val p1: Double   // Longitude of sun's perigee
    )

    /**
     * Calculate fundamental astronomical arguments for a given time.
     *
     * Based on Meeus "Astronomical Algorithms" polynomial expressions,
     * matching XTide astronomical argument calculations.
     *
     * @param time UTC time
     * @return Astronomical arguments in degrees
     */
    private fun getAstronomicalArguments(time: Instant): AstronomicalArguments {
        // Calculate time in Julian centuries from J2000.0
        val daysSinceJ2000 = (time.epochSecond - J2000_EPOCH.epochSecond).toDouble() / 86400.0
        val T = daysSinceJ2000 / 36525.0 // Julian centuries

        // Compute s and h FIRST - needed for tau calculation
        // Mean longitude of moon (s) - degrees (Meeus formula 45.1)
        val s = 218.3164477 + 481267.88123421 * T -
                0.0015786 * T * T +
                T * T * T / 538841.0 -
                T * T * T * T / 65194000.0

        // Mean longitude of sun (h) - degrees (Meeus formula 24.2)
        val h = 280.46646 + 36000.76983 * T + 0.0003032 * T * T

        // Longitude of moon's perigee (p) - degrees
        val p = 83.3532465 + 4069.0137287 * T -
                0.0103200 * T * T -
                T * T * T / 80053.0 +
                T * T * T * T / 18999000.0

        // Longitude of moon's ascending node (N) - degrees (Meeus formula 45.7)
        // Note: This is the actual node longitude, NOT negated.
        // The Doodson convention for N' uses this value directly.
        val N = 125.04452 - 1934.136261 * T +
                0.0020708 * T * T +
                T * T * T / 450000.0

        // Longitude of sun's perigee (p1) - degrees
        val p1 = 282.93735 + 1.71946 * T + 0.00046 * T * T

        // Mean lunar time (Greenwich hour angle of mean Moon) - degrees
        // τ = 15° × UT + h - s (Schureman 1958)
        // This ensures M2 speed = 2τ̇ = 28.984°/hr
        val utHours = time.atZone(ZoneOffset.UTC).hour +
                      time.atZone(ZoneOffset.UTC).minute / 60.0 +
                      time.atZone(ZoneOffset.UTC).second / 3600.0
        val tau = 15.0 * utHours + h - s  // Use un-normalized h and s

        return AstronomicalArguments(
            T = tau,  // Do NOT normalize - must maintain continuity across day boundaries
            s = normalizeAngle(s),
            h = normalizeAngle(h),
            p = normalizeAngle(p),
            N = normalizeAngle(N),
            p1 = normalizeAngle(p1)
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
