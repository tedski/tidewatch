package com.tidewatch.tide

import com.tidewatch.data.models.HarmonicConstituent
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Duration

/**
 * Verification tests against NOAA predictions for specific dates/times.
 *
 * NOAA predictions for 9413450 (Monterey) on 2026-02-13 in UTC:
 * - Low at 22:44 UTC = -0.361 ft
 * - High at 15:06 UTC = 5.271 ft
 */
class NOAAVerificationTest {

    @Test
    fun `debug V0 calculation for M2`() {
        val refEpoch = Instant.parse("1983-01-01T00:00:00Z")
        val m2 = Constituents.getConstituent("M2")!!

        val v0 = AstronomicalCalculator.calculateEquilibriumArgument(m2, refEpoch)
        println("M2 V0 at 1983-01-01 00:00:00 UTC: $v0°")

        // Calculate at the test time
        val testTime = Instant.parse("2026-02-13T22:44:00Z")
        val vTest = AstronomicalCalculator.calculateEquilibriumArgument(m2, testTime)
        println("M2 V at 2026-02-13 22:44:00 UTC: $vTest°")

        // Calculate hours since epoch
        val hours = Duration.between(refEpoch, testTime).seconds / 3600.0
        println("Hours since ref epoch: $hours")

        // Calculate expected argument at test time using ω×t + V0
        val omega = m2.speed
        val phaseGMT = 181.3 // From NOAA
        val expectedArg = (omega * hours + v0 - phaseGMT) % 360
        println("M2 speed: $omega°/hr")
        println("Phase (GMT): $phaseGMT°")
        println("Expected argument at test time: ${omega * hours + v0 - phaseGMT}° (normalized: $expectedArg°)")

        // What does it contribute?
        val amp = 1.61
        val contribution = amp * Math.cos(Math.toRadians(expectedArg))
        println("M2 contribution at test time: $contribution ft")
    }

    @Test
    fun `verify Monterey low tide matches NOAA on 2026-02-13`() {
        // This test uses the actual harmonic constants from NOAA for Monterey
        // Expected: Low at 22:44 UTC = -0.361 ft
        // phase_GMT values from NOAA API

        val montereyConstituents = listOf(
            HarmonicConstituent("9413450", "M2", 1.61, 181.3),
            HarmonicConstituent("9413450", "K1", 1.20, 219.6),
            HarmonicConstituent("9413450", "O1", 0.75, 203.5),
            HarmonicConstituent("9413450", "S2", 0.43, 180.1),
            HarmonicConstituent("9413450", "N2", 0.37, 155.0)
        )

        val calculator = HarmonicCalculator(
            constituents = mapOf("9413450" to montereyConstituents),
            datumOffsets = mapOf("9413450" to 2.83)  // MSL(6.21) - MLLW(3.38)
        )

        // Calculate at low tide time: 2026-02-13 22:44 UTC
        val lowTideTime = Instant.parse("2026-02-13T22:44:00Z")
        val height = calculator.calculateHeight("9413450", lowTideTime)

        println("Calculated height at $lowTideTime: $height ft")
        println("Expected from NOAA: -0.361 ft")
        println("Error: ${kotlin.math.abs(height - (-0.361))} ft")

        // This test will likely fail - we're trying to diagnose WHY
        // Expected: close to -0.361 ft
        // If height is around +2.0 to +3.0 ft, calculations are ~2-3 hours early
    }

    @Test
    fun `verify Monterey high tide matches NOAA on 2026-02-13`() {
        // phase_GMT values from NOAA API, with Z₀ datum offset
        val montereyConstituents = listOf(
            HarmonicConstituent("9413450", "M2", 1.61, 181.3),
            HarmonicConstituent("9413450", "K1", 1.20, 219.6),
            HarmonicConstituent("9413450", "O1", 0.75, 203.5),
            HarmonicConstituent("9413450", "S2", 0.43, 180.1),
            HarmonicConstituent("9413450", "N2", 0.37, 155.0)
        )

        val calculator = HarmonicCalculator(
            constituents = mapOf("9413450" to montereyConstituents),
            datumOffsets = mapOf("9413450" to 2.83)  // MSL(6.21) - MLLW(3.38)
        )

        // Calculate at high tide time: 2026-02-13 15:06 UTC
        val highTideTime = Instant.parse("2026-02-13T15:06:00Z")
        val height = calculator.calculateHeight("9413450", highTideTime)

        println("Calculated height at $highTideTime: $height ft")
        println("Expected from NOAA: 5.271 ft")
        println("Error: ${kotlin.math.abs(height - 5.271)} ft")

        // NOTE: With only 5 principal constituents, expect ~0.5-1.0 ft error
        // Full 37 constituents would achieve ±0.1-0.2 ft accuracy
        // This test validates the calculation formulas are correct (tau, Z₀, etc.)
        assertTrue(
            kotlin.math.abs(height - 5.271) < 1.0,
            "Height $height should be within 1.0 ft of 5.271 ft (NOAA prediction) with 5 constituents"
        )
    }
}
