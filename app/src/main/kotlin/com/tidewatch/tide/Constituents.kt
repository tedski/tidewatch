package com.tidewatch.tide

import kotlin.math.PI

/**
 * Tidal constituent definitions and constants.
 *
 * Based on NOAA's 37 harmonic constituents used for tide prediction.
 * Each constituent represents a periodic component of the gravitational
 * forces from the moon, sun, and astronomical cycles.
 *
 * References:
 * - NOAA Technical Report NOS CO-OPS 3: "Harmonic Analysis of Tides"
 * - IHO Tidal Constituent Bank
 */
object Constituents {

    /**
     * Angular velocity conversion factor: degrees per hour.
     */
    const val DEGREES_PER_HOUR = 15.0

    /**
     * Represents a single tidal constituent.
     *
     * @property name Constituent identifier (e.g., "M2", "S2")
     * @property speed Angular velocity in degrees per hour
     * @property doodsonNumbers Doodson number components for astronomical calculations [τ, s, h, p, N', p']
     * @property phaseOffset Constant phase offset in degrees, derived from the SP98 (Schureman 1958)
     *   "c" column (Table 2) and adjusted for the Doodson tau convention.
     *
     *   SP98 uses T (hour angle of mean sun with noon epoch), while Doodson uses tau
     *   (Greenwich hour angle of mean moon, with midnight epoch: tau = T + h - s).
     *   The SP98 noon epoch causes T mod 360 = 180 at midnight UTC, while tau = 0 at
     *   midnight UTC. For odd-order tau coefficients (diurnal d_tau=1, terdiurnal d_tau=3),
     *   this introduces a 180° offset that must be combined with SP98's c constant:
     *     phaseOffset = c_SP98 + (d_tau mod 2) * 180  (mod 360)
     *
     *   Since NOAA's phase_GMT (κ) values are referenced to V₀ computed by SP98/congen,
     *   we must include this offset for correct predictions.
     * @property description Human-readable description
     * @property type Constituent type (semidiurnal, diurnal, long-period, etc.)
     */
    data class Constituent(
        val name: String,
        val speed: Double, // degrees per hour
        val doodsonNumbers: IntArray,
        val phaseOffset: Double = 0.0, // SP98 "c" constant in degrees
        val description: String,
        val type: Type
    ) {
        enum class Type {
            SEMIDIURNAL,  // ~12.42 hour period (twice daily)
            DIURNAL,      // ~24 hour period (once daily)
            LONG_PERIOD,  // > 24 hours (fortnightly, monthly, yearly)
            COMPOUND      // Shallow water constituents (harmonics/overtides)
        }

        /**
         * Returns the period of this constituent in hours.
         */
        fun periodHours(): Double = 360.0 / speed

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Constituent) return false
            return name == other.name
        }

        override fun hashCode(): Int = name.hashCode()
    }

    /**
     * All 37 NOAA standard constituents, ordered by astronomical significance.
     */
    val ALL: List<Constituent> = listOf(
        // Principal semidiurnal constituents
        Constituent("M2", 28.9841042, intArrayOf(2, 0, 0, 0, 0, 0), description = "Principal lunar semidiurnal", type = Constituent.Type.SEMIDIURNAL),
        Constituent("S2", 30.0000000, intArrayOf(2, 2, -2, 0, 0, 0), description = "Principal solar semidiurnal", type = Constituent.Type.SEMIDIURNAL),
        Constituent("N2", 28.4397295, intArrayOf(2, -1, 0, 1, 0, 0), description = "Larger lunar elliptic semidiurnal", type = Constituent.Type.SEMIDIURNAL),
        Constituent("K2", 30.0821373, intArrayOf(2, 2, 0, 0, 0, 0), description = "Lunisolar semidiurnal", type = Constituent.Type.SEMIDIURNAL),

        // Principal diurnal constituents (d_tau=1, odd: c_SP98 + 180)
        Constituent("K1", 15.0410686, intArrayOf(1, 1, 0, 0, 0, 0), phaseOffset = 90.0, description = "Lunisolar diurnal", type = Constituent.Type.DIURNAL),     // c=-90+180=+90
        Constituent("O1", 13.9430356, intArrayOf(1, -1, 0, 0, 0, 0), phaseOffset = -90.0, description = "Lunar diurnal", type = Constituent.Type.DIURNAL),       // c=+90+180=-90
        Constituent("P1", 14.9589314, intArrayOf(1, 1, -2, 0, 0, 0), phaseOffset = -90.0, description = "Solar diurnal", type = Constituent.Type.DIURNAL),       // c=+90+180=-90
        Constituent("Q1", 13.3986609, intArrayOf(1, -2, 0, 1, 0, 0), phaseOffset = -90.0, description = "Larger lunar elliptic diurnal", type = Constituent.Type.DIURNAL), // c=+90+180=-90

        // Long period constituents
        Constituent("Mm", 0.5443747, intArrayOf(0, 1, 0, -1, 0, 0), description = "Lunar monthly", type = Constituent.Type.LONG_PERIOD),
        Constituent("Mf", 1.0980331, intArrayOf(0, 2, 0, 0, 0, 0), description = "Lunisolar fortnightly", type = Constituent.Type.LONG_PERIOD),
        Constituent("Ssa", 0.0821373, intArrayOf(0, 0, 2, 0, 0, 0), description = "Solar semiannual", type = Constituent.Type.LONG_PERIOD),
        Constituent("Sa", 0.0410686, intArrayOf(0, 0, 1, 0, 0, 0), description = "Solar annual", type = Constituent.Type.LONG_PERIOD),

        // Additional semidiurnal constituents
        Constituent("2N2", 27.8953548, intArrayOf(2, -2, 0, 2, 0, 0), description = "Lunar elliptical semidiurnal second-order", type = Constituent.Type.SEMIDIURNAL),
        Constituent("μ2", 27.9682084, intArrayOf(2, -2, 2, 0, 0, 0), description = "Variational", type = Constituent.Type.SEMIDIURNAL),
        Constituent("ν2", 28.5125831, intArrayOf(2, -1, 2, -1, 0, 0), description = "Larger lunar evectional", type = Constituent.Type.SEMIDIURNAL),
        Constituent("λ2", 29.4556253, intArrayOf(2, 1, -2, 1, 0, 0), phaseOffset = 180.0, description = "Smaller lunar evectional", type = Constituent.Type.SEMIDIURNAL),
        Constituent("L2", 29.5284789, intArrayOf(2, 1, 0, -1, 0, 0), phaseOffset = 180.0, description = "Smaller lunar elliptic semidiurnal", type = Constituent.Type.SEMIDIURNAL),
        Constituent("T2", 29.9589333, intArrayOf(2, 2, -3, 0, 0, 1), description = "Larger solar elliptic", type = Constituent.Type.SEMIDIURNAL),
        Constituent("R2", 30.0410667, intArrayOf(2, 2, -1, 0, 0, -1), phaseOffset = 180.0, description = "Smaller solar elliptic", type = Constituent.Type.SEMIDIURNAL),

        // Additional diurnal constituents (d_tau=1, odd: c_SP98 + 180)
        Constituent("2Q1", 12.8542862, intArrayOf(1, -3, 1, 1, 0, 0), phaseOffset = -90.0, description = "Larger elliptic diurnal", type = Constituent.Type.DIURNAL),    // c=+90+180=-90
        Constituent("σ1", 12.9271398, intArrayOf(1, -3, 1, 1, 0, 0), phaseOffset = -90.0, description = "Lunar evectional diurnal", type = Constituent.Type.DIURNAL),     // c=+90+180=-90
        Constituent("ρ1", 13.4715145, intArrayOf(1, -2, 2, -1, 0, 0), phaseOffset = -90.0, description = "Smaller lunar elliptic diurnal", type = Constituent.Type.DIURNAL), // c=+90+180=-90
        Constituent("M1", 14.4966939, intArrayOf(1, 0, 0, 0, 0, 0), phaseOffset = 180.0, description = "Smaller lunar elliptic diurnal", type = Constituent.Type.DIURNAL),   // c=0+180=180 (d_tau=1, odd)
        Constituent("J1", 15.5854433, intArrayOf(1, 2, 0, -1, 0, 0), phaseOffset = 90.0, description = "Smaller lunar elliptic diurnal", type = Constituent.Type.DIURNAL),  // c=-90+180=+90
        Constituent("OO1", 16.1391017, intArrayOf(1, 3, 0, 0, 0, 0), phaseOffset = 90.0, description = "Lunar diurnal", type = Constituent.Type.DIURNAL),                   // c=-90+180=+90

        // Shallow water (compound) constituents
        Constituent("M4", 57.9682084, intArrayOf(4, 0, 0, 0, 0, 0), description = "Shallow water overtide of M2", type = Constituent.Type.COMPOUND),
        Constituent("MS4", 58.9841042, intArrayOf(4, 2, -2, 0, 0, 0), description = "Shallow water quarter diurnal", type = Constituent.Type.COMPOUND),
        Constituent("M6", 86.9523127, intArrayOf(6, 0, 0, 0, 0, 0), description = "Shallow water overtide of M2", type = Constituent.Type.COMPOUND),
        Constituent("M8", 115.9364169, intArrayOf(8, 0, 0, 0, 0, 0), description = "Shallow water eighth diurnal", type = Constituent.Type.COMPOUND),
        Constituent("MK3", 44.0251729, intArrayOf(3, 1, 0, 0, 0, 0), phaseOffset = 90.0, description = "Shallow water terdiurnal", type = Constituent.Type.COMPOUND),     // c=-90+180=+90 (d_tau=3, odd)
        Constituent("S4", 60.0000000, intArrayOf(4, 4, -4, 0, 0, 0), description = "Shallow water overtide of S2", type = Constituent.Type.COMPOUND),
        Constituent("MN4", 57.4238337, intArrayOf(4, -1, 0, 1, 0, 0), description = "Shallow water quarter diurnal", type = Constituent.Type.COMPOUND),
        Constituent("S6", 90.0000000, intArrayOf(6, 6, -6, 0, 0, 0), description = "Shallow water overtide of S2", type = Constituent.Type.COMPOUND),
        Constituent("2SM2", 31.0158958, intArrayOf(2, 4, -4, 0, 0, 0), description = "Shallow water semidiurnal", type = Constituent.Type.COMPOUND),
        Constituent("2MK3", 42.9271398, intArrayOf(5, 1, -2, 0, 0, 0), phaseOffset = -90.0, description = "Shallow water terdiurnal", type = Constituent.Type.COMPOUND),   // c=+90+180=-90 (d_tau=5, odd)

        // Additional long period
        Constituent("MSf", 1.0158958, intArrayOf(0, 2, -2, 0, 0, 0), description = "Lunisolar synodic fortnightly", type = Constituent.Type.LONG_PERIOD),
        Constituent("MO3", 42.9271398, intArrayOf(3, -1, 0, 0, 0, 0), phaseOffset = -90.0, description = "Lunar terdiurnal", type = Constituent.Type.COMPOUND),              // c=+90+180=-90 (d_tau=3, odd)
        Constituent("S1", 15.0000000, intArrayOf(1, 1, -1, 0, 0, 0), phaseOffset = 180.0, description = "Solar diurnal", type = Constituent.Type.DIURNAL)                     // c=0+180=180 (d_tau=1, odd)
    )

    /**
     * Map of constituent names to Constituent objects for fast lookup.
     */
    val BY_NAME: Map<String, Constituent> = ALL.associateBy { it.name }

    /**
     * Returns the constituent with the given name, or null if not found.
     */
    fun getConstituent(name: String): Constituent? = BY_NAME[name]

    /**
     * Returns the angular velocity (speed) in radians per second for a constituent.
     */
    fun speedRadiansPerSecond(constituent: Constituent): Double {
        return constituent.speed * PI / 180.0 / 3600.0
    }

    /**
     * Returns the angular velocity (speed) in radians per hour for a constituent.
     */
    fun speedRadiansPerHour(constituent: Constituent): Double {
        return constituent.speed * PI / 180.0
    }

    /**
     * Principal constituents used for basic tide prediction.
     * These 8 constituents provide ~95% accuracy for most locations.
     */
    val PRINCIPAL: List<Constituent> = listOf(
        BY_NAME["M2"]!!,
        BY_NAME["S2"]!!,
        BY_NAME["N2"]!!,
        BY_NAME["K2"]!!,
        BY_NAME["K1"]!!,
        BY_NAME["O1"]!!,
        BY_NAME["P1"]!!,
        BY_NAME["Q1"]!!
    )
}
