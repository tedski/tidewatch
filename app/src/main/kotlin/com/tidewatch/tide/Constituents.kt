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
     * @property description Human-readable description
     * @property type Constituent type (semidiurnal, diurnal, long-period, etc.)
     */
    data class Constituent(
        val name: String,
        val speed: Double, // degrees per hour
        val doodsonNumbers: IntArray,
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
        Constituent("M2", 28.9841042, intArrayOf(2, 0, 0, 0, 0, 0), "Principal lunar semidiurnal", Constituent.Type.SEMIDIURNAL),
        Constituent("S2", 30.0000000, intArrayOf(2, 2, -2, 0, 0, 0), "Principal solar semidiurnal", Constituent.Type.SEMIDIURNAL),
        Constituent("N2", 28.4397295, intArrayOf(2, -1, 0, 1, 0, 0), "Larger lunar elliptic semidiurnal", Constituent.Type.SEMIDIURNAL),
        Constituent("K2", 30.0821373, intArrayOf(2, 2, 0, 0, 0, 0), "Lunisolar semidiurnal", Constituent.Type.SEMIDIURNAL),

        // Principal diurnal constituents
        Constituent("K1", 15.0410686, intArrayOf(1, 1, 0, 0, 0, 0), "Lunisolar diurnal", Constituent.Type.DIURNAL),
        Constituent("O1", 13.9430356, intArrayOf(1, -1, 0, 0, 0, 0), "Lunar diurnal", Constituent.Type.DIURNAL),
        Constituent("P1", 14.9589314, intArrayOf(1, 1, -2, 0, 0, 0), "Solar diurnal", Constituent.Type.DIURNAL),
        Constituent("Q1", 13.3986609, intArrayOf(1, -2, 0, 1, 0, 0), "Larger lunar elliptic diurnal", Constituent.Type.DIURNAL),

        // Long period constituents
        Constituent("Mm", 0.5443747, intArrayOf(0, 1, 0, -1, 0, 0), "Lunar monthly", Constituent.Type.LONG_PERIOD),
        Constituent("Mf", 1.0980331, intArrayOf(0, 2, 0, 0, 0, 0), "Lunisolar fortnightly", Constituent.Type.LONG_PERIOD),
        Constituent("Ssa", 0.0821373, intArrayOf(0, 0, 2, 0, 0, 0), "Solar semiannual", Constituent.Type.LONG_PERIOD),
        Constituent("Sa", 0.0410686, intArrayOf(0, 0, 1, 0, 0, -1), "Solar annual", Constituent.Type.LONG_PERIOD),

        // Additional semidiurnal constituents
        Constituent("2N2", 27.8953548, intArrayOf(2, -2, 0, 2, 0, 0), "Lunar elliptical semidiurnal second-order", Constituent.Type.SEMIDIURNAL),
        Constituent("μ2", 27.9682084, intArrayOf(2, -2, 2, 0, 0, 0), "Variational", Constituent.Type.SEMIDIURNAL),
        Constituent("ν2", 28.5125831, intArrayOf(2, -1, 2, -1, 0, 0), "Larger lunar evectional", Constituent.Type.SEMIDIURNAL),
        Constituent("λ2", 29.4556253, intArrayOf(2, 1, -2, 1, 0, 0), "Smaller lunar evectional", Constituent.Type.SEMIDIURNAL),
        Constituent("L2", 29.5284789, intArrayOf(2, 1, 0, -1, 0, 0), "Smaller lunar elliptic semidiurnal", Constituent.Type.SEMIDIURNAL),
        Constituent("T2", 29.9589333, intArrayOf(2, 2, -3, 0, 0, 1), "Larger solar elliptic", Constituent.Type.SEMIDIURNAL),
        Constituent("R2", 30.0410667, intArrayOf(2, 2, -1, 0, 0, -1), "Smaller solar elliptic", Constituent.Type.SEMIDIURNAL),

        // Additional diurnal constituents
        Constituent("2Q1", 12.8542862, intArrayOf(1, -3, 0, 2, 0, 0), "Larger elliptic diurnal", Constituent.Type.DIURNAL),
        Constituent("σ1", 12.9271398, intArrayOf(1, -3, 2, 0, 0, 0), "Lunar evectional diurnal", Constituent.Type.DIURNAL),
        Constituent("ρ1", 13.4715145, intArrayOf(1, -2, 2, -1, 0, 0), "Smaller lunar elliptic diurnal", Constituent.Type.DIURNAL),
        Constituent("M1", 14.4966939, intArrayOf(1, 0, 0, 0, 0, 0), "Smaller lunar elliptic diurnal", Constituent.Type.DIURNAL),
        Constituent("J1", 15.5854433, intArrayOf(1, 2, 0, -1, 0, 0), "Smaller lunar elliptic diurnal", Constituent.Type.DIURNAL),
        Constituent("OO1", 16.1391017, intArrayOf(1, 3, 0, 0, 0, 0), "Lunar diurnal", Constituent.Type.DIURNAL),

        // Shallow water (compound) constituents
        Constituent("M4", 57.9682084, intArrayOf(4, 0, 0, 0, 0, 0), "Shallow water overtide of M2", Constituent.Type.COMPOUND),
        Constituent("MS4", 58.9841042, intArrayOf(4, 2, -2, 0, 0, 0), "Shallow water quarter diurnal", Constituent.Type.COMPOUND),
        Constituent("M6", 86.9523127, intArrayOf(6, 0, 0, 0, 0, 0), "Shallow water overtide of M2", Constituent.Type.COMPOUND),
        Constituent("M8", 115.9364169, intArrayOf(8, 0, 0, 0, 0, 0), "Shallow water eighth diurnal", Constituent.Type.COMPOUND),
        Constituent("MK3", 44.0251729, intArrayOf(3, 1, 0, 0, 0, 0), "Shallow water terdiurnal", Constituent.Type.COMPOUND),
        Constituent("S4", 60.0000000, intArrayOf(4, 4, -4, 0, 0, 0), "Shallow water overtide of S2", Constituent.Type.COMPOUND),
        Constituent("MN4", 57.4238337, intArrayOf(4, -1, 0, 1, 0, 0), "Shallow water quarter diurnal", Constituent.Type.COMPOUND),
        Constituent("S6", 90.0000000, intArrayOf(6, 6, -6, 0, 0, 0), "Shallow water overtide of S2", Constituent.Type.COMPOUND),
        Constituent("2SM2", 31.0158958, intArrayOf(2, 4, -4, 0, 0, 0), "Shallow water semidiurnal", Constituent.Type.COMPOUND),
        Constituent("2MK3", 42.9271398, intArrayOf(3, 0, 2, 0, 0, 0), "Shallow water terdiurnal", Constituent.Type.COMPOUND),

        // Additional long period
        Constituent("MSf", 1.0158958, intArrayOf(0, 2, -2, 0, 0, 0), "Lunisolar synodic fortnightly", Constituent.Type.LONG_PERIOD),
        Constituent("MO3", 42.9271398, intArrayOf(3, -1, 0, 0, 0, 0), "Lunar terdiurnal", Constituent.Type.COMPOUND),
        Constituent("S1", 15.0000000, intArrayOf(1, 1, -1, 0, 0, 1), "Solar diurnal", Constituent.Type.DIURNAL)
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
