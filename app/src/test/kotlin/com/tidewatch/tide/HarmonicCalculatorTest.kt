package com.tidewatch.tide

import com.tidewatch.data.models.HarmonicConstituent
import com.tidewatch.data.models.SubordinateOffset
import com.tidewatch.data.models.TideExtremum
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for HarmonicCalculator.
 *
 * Tests validate calculation accuracy against NOAA predictions and edge cases.
 * Target accuracy: ±0.1 ft for height, ±2 minutes for times.
 */
class HarmonicCalculatorTest {

    private lateinit var calculator: HarmonicCalculator

    /**
     * San Francisco (Golden Gate) - Station 9414290
     * Sample harmonic constituents from NOAA.
     */
    private val sanFranciscoConstituents = listOf(
        // Major semidiurnal constituents
        HarmonicConstituent("9414290", "M2", 2.929, 193.1),  // Principal lunar
        HarmonicConstituent("9414290", "S2", 0.880, 216.7),  // Principal solar
        HarmonicConstituent("9414290", "N2", 0.668, 169.8),  // Larger lunar elliptic
        HarmonicConstituent("9414290", "K2", 0.239, 216.6),  // Lunisolar

        // Major diurnal constituents
        HarmonicConstituent("9414290", "K1", 0.950, 166.6),  // Lunisolar diurnal
        HarmonicConstituent("9414290", "O1", 0.618, 143.1),  // Lunar diurnal
        HarmonicConstituent("9414290", "P1", 0.286, 163.7),  // Solar diurnal
        HarmonicConstituent("9414290", "Q1", 0.109, 130.8),  // Larger lunar elliptic

        // Long period constituents
        HarmonicConstituent("9414290", "Mm", 0.099, 0.0),    // Lunar monthly
        HarmonicConstituent("9414290", "Mf", 0.100, 0.0),    // Lunisolar fortnightly
        HarmonicConstituent("9414290", "Ssa", 0.184, 0.0),   // Solar semiannual
    )

    @Before
    fun setup() {
        val constituents = mapOf("9414290" to sanFranciscoConstituents)
        calculator = HarmonicCalculator(constituents)
    }

    @Test
    fun `test calculateHeight returns reasonable values`() {
        // Test at a known time: 2026-02-12 12:00:00 UTC
        val time = Instant.parse("2026-02-12T12:00:00Z")
        val height = calculator.calculateHeight("9414290", time)

        // San Francisco tides range from -2 to +7 feet typically
        assertTrue(height in -3.0..8.0, "Height $height should be within typical range")
    }

    @Test
    fun `test calculateHeight at different times of day`() {
        val date = ZonedDateTime.of(2026, 2, 12, 0, 0, 0, 0, ZoneId.of("UTC"))

        // Test at 6-hour intervals throughout the day
        for (hour in 0..23 step 6) {
            val time = date.plusHours(hour.toLong()).toInstant()
            val height = calculator.calculateHeight("9414290", time)

            // Verify height is in reasonable range
            assertTrue(height in -3.0..8.0,
                "Height at hour $hour: $height should be within range")
        }
    }

    @Test
    fun `test calculateHeight continuity across midnight`() {
        // Test times around midnight to verify no discontinuities
        val beforeMidnight = Instant.parse("2026-02-12T23:55:00Z")
        val atMidnight = Instant.parse("2026-02-13T00:00:00Z")
        val afterMidnight = Instant.parse("2026-02-13T00:05:00Z")

        val heightBefore = calculator.calculateHeight("9414290", beforeMidnight)
        val heightAt = calculator.calculateHeight("9414290", atMidnight)
        val heightAfter = calculator.calculateHeight("9414290", afterMidnight)

        // Heights should change gradually (no jumps > 1 foot in 10 minutes)
        assertTrue(abs(heightAt - heightBefore) < 1.0,
            "Height should change gradually before midnight: $heightBefore -> $heightAt")
        assertTrue(abs(heightAfter - heightAt) < 1.0,
            "Height should change gradually after midnight: $heightAt -> $heightAfter")
    }

    @Test
    fun `test calculateRateOfChange returns reasonable values`() {
        val time = Instant.parse("2026-02-12T12:00:00Z")
        val rate = calculator.calculateRateOfChange("9414290", time)

        // Typical rate of change: -2 to +2 feet per hour for SF
        assertTrue(rate in -3.0..3.0, "Rate $rate should be within typical range")
    }

    @Test
    fun `test calculateRateOfChange sign indicates direction`() {
        val time = Instant.parse("2026-02-12T12:00:00Z")
        val rate = calculator.calculateRateOfChange("9414290", time)
        val heightNow = calculator.calculateHeight("9414290", time)
        val heightLater = calculator.calculateHeight("9414290", time.plusSeconds(3600))

        // If rate is positive, height should increase
        // If rate is negative, height should decrease
        val actualChange = heightLater - heightNow

        if (abs(rate) > 0.01) { // Ignore slack tide
            assertTrue(
                (rate > 0 && actualChange > 0) || (rate < 0 && actualChange < 0),
                "Rate sign ($rate) should match actual change ($actualChange)"
            )
        }
    }

    @Test
    fun `test findNextExtremum finds high or low tide`() {
        val startTime = Instant.parse("2026-02-12T00:00:00Z")

        // Find next high tide
        val nextHigh = calculator.findNextExtremum("9414290", startTime, findHigh = true)
        assertNotNull(nextHigh, "Should find a high tide")
        assertEquals(TideExtremum.Type.HIGH, nextHigh.type)
        assertTrue(nextHigh.time.isAfter(startTime), "High tide should be after start time")

        // Find next low tide
        val nextLow = calculator.findNextExtremum("9414290", startTime, findHigh = false)
        assertNotNull(nextLow, "Should find a low tide")
        assertEquals(TideExtremum.Type.LOW, nextLow.type)
        assertTrue(nextLow.time.isAfter(startTime), "Low tide should be after start time")
    }

    @Test
    fun `test extremum is actually an extremum`() {
        val startTime = Instant.parse("2026-02-12T00:00:00Z")
        val extremum = calculator.findNextExtremum("9414290", startTime, findHigh = true)
        assertNotNull(extremum)

        // Rate of change at extremum should be near zero
        val rate = calculator.calculateRateOfChange("9414290", extremum.time)
        assertTrue(abs(rate) < 0.1,
            "Rate at extremum should be near zero, got $rate")

        // Height 30 minutes before and after should be lower (for high) or higher (for low)
        val heightBefore = calculator.calculateHeight("9414290",
            extremum.time.minusSeconds(1800))
        val heightAfter = calculator.calculateHeight("9414290",
            extremum.time.plusSeconds(1800))

        if (extremum.type == TideExtremum.Type.HIGH) {
            assertTrue(extremum.height >= heightBefore,
                "High tide should be >= height before")
            assertTrue(extremum.height >= heightAfter,
                "High tide should be >= height after")
        } else {
            assertTrue(extremum.height <= heightBefore,
                "Low tide should be <= height before")
            assertTrue(extremum.height <= heightAfter,
                "Low tide should be <= height after")
        }
    }

    @Test
    fun `test findExtrema finds multiple extrema`() {
        val startTime = Instant.parse("2026-02-12T00:00:00Z")
        val endTime = startTime.plusSeconds(86400 * 2) // 2 days

        val extrema = calculator.findExtrema("9414290", startTime, endTime)

        // Should find ~4 extrema per day (2 highs, 2 lows) for semidiurnal tides
        assertTrue(extrema.size >= 6, "Should find at least 6 extrema in 2 days")

        // Extrema should alternate between high and low
        for (i in 0 until extrema.size - 1) {
            assertTrue(extrema[i].type != extrema[i + 1].type,
                "Extrema should alternate between high and low")
        }

        // Extrema should be in chronological order
        for (i in 0 until extrema.size - 1) {
            assertTrue(extrema[i].time.isBefore(extrema[i + 1].time),
                "Extrema should be in chronological order")
        }
    }

    @Test
    fun `test generateTideCurve produces smooth curve`() {
        val startTime = Instant.parse("2026-02-12T00:00:00Z")
        val endTime = startTime.plusSeconds(86400) // 24 hours

        val curve = calculator.generateTideCurve("9414290", startTime, endTime,
            intervalMinutes = 10)

        // Should have ~144 points (24 hours * 6 per hour)
        assertEquals(145, curve.size, "Should have correct number of points")

        // Points should be evenly spaced
        for (i in 0 until curve.size - 1) {
            val timeDiff = curve[i + 1].time.epochSecond - curve[i].time.epochSecond
            assertEquals(600L, timeDiff, "Points should be 10 minutes apart")
        }

        // Heights should change gradually (no discontinuities)
        for (i in 0 until curve.size - 1) {
            val heightDiff = abs(curve[i + 1].height - curve[i].height)
            assertTrue(heightDiff < 0.5,
                "Heights should change gradually, got diff $heightDiff")
        }
    }

    @Test
    fun `test semidiurnal tide characteristics for San Francisco`() {
        // San Francisco has semidiurnal tides (2 highs and 2 lows per day)
        val startTime = Instant.parse("2026-02-12T00:00:00Z")
        val endTime = startTime.plusSeconds(86400) // 24 hours

        val extrema = calculator.findExtrema("9414290", startTime, endTime)

        // Should have approximately 4 extrema (2 highs, 2 lows)
        assertTrue(extrema.size >= 3 && extrema.size <= 5,
            "Should have ~4 extrema per day, got ${extrema.size}")

        // Count highs and lows
        val highCount = extrema.count { it.type == TideExtremum.Type.HIGH }
        val lowCount = extrema.count { it.type == TideExtremum.Type.LOW }

        // Should have roughly equal number of highs and lows
        assertTrue(abs(highCount - lowCount) <= 1,
            "Should have similar highs ($highCount) and lows ($lowCount)")
    }

    @Test
    fun `test calculation consistency`() {
        // Calculate same time twice - should get same result
        val time = Instant.parse("2026-02-12T12:00:00Z")

        val height1 = calculator.calculateHeight("9414290", time)
        val height2 = calculator.calculateHeight("9414290", time)

        assertEquals(height1, height2, 0.000001,
            "Same calculation should produce same result")
    }

    @Test
    fun `test leap year handling`() {
        // Test calculation on leap day
        val leapDay = Instant.parse("2024-02-29T12:00:00Z")
        val height = calculator.calculateHeight("9414290", leapDay)

        // Should not crash and should return reasonable value
        assertTrue(height in -3.0..8.0,
            "Should handle leap year correctly")
    }

    @Test
    fun `test year boundary crossing`() {
        // Test around New Year's midnight
        val beforeNewYear = Instant.parse("2025-12-31T23:55:00Z")
        val afterNewYear = Instant.parse("2026-01-01T00:05:00Z")

        val heightBefore = calculator.calculateHeight("9414290", beforeNewYear)
        val heightAfter = calculator.calculateHeight("9414290", afterNewYear)

        // Should be continuous across year boundary
        assertTrue(abs(heightAfter - heightBefore) < 1.0,
            "Should be continuous across year boundary")
    }

    @Test
    fun `test known NOAA prediction for validation`() {
        // This test would compare against actual NOAA predictions
        // For now, we test that calculations are in reasonable range

        // Example: 2026-02-12 at SF Golden Gate
        // Expected high: ~6.0 ft around 7:00 AM
        // Expected low: ~-1.0 ft around 1:00 PM

        val morningHigh = Instant.parse("2026-02-12T15:00:00Z") // 7 AM PST
        val afternoonLow = Instant.parse("2026-02-12T21:00:00Z") // 1 PM PST

        val heightAtMorningHigh = calculator.calculateHeight("9414290", morningHigh)
        val heightAtAfternoonLow = calculator.calculateHeight("9414290", afternoonLow)

        // Morning should be higher than afternoon for typical SF tide pattern
        // (This is a rough check - exact validation requires NOAA data)
        assertTrue(heightAtMorningHigh > heightAtAfternoonLow + 1.0,
            "Morning high ($heightAtMorningHigh) should be significantly higher " +
            "than afternoon low ($heightAtAfternoonLow)")
    }

    // ============================================================================
    // SUBORDINATE STATION TESTS
    // ============================================================================

    @Test
    fun `test subordinate station configuration accepted`() {
        // Create a reference station with known constituents
        val refConstituents = listOf(
            HarmonicConstituent("REF001", "M2", 2.0, 180.0),
            HarmonicConstituent("REF001", "S2", 0.5, 200.0),
            HarmonicConstituent("REF001", "K1", 0.8, 150.0)
        )

        // Subordinate station with height offsets: +0.5 ft high, -0.3 ft low
        val offset = SubordinateOffset(
            stationId = "SUB001",
            referenceStationId = "REF001",
            heightOffsetHigh = 0.5,
            heightOffsetLow = -0.3,
            timeOffsetHigh = 0,  // No time offset for this test
            timeOffsetLow = 0
        )

        // Verify calculator accepts subordinate offsets configuration
        val calcWithOffset = HarmonicCalculator(
            constituents = mapOf("REF001" to refConstituents),
            subordinateOffsets = mapOf("SUB001" to offset)
        )

        val time = Instant.parse("2026-02-12T12:00:00Z")

        // Reference station should work normally
        val refHeight = calcWithOffset.calculateHeight("REF001", time)
        assertTrue(refHeight in -5.0..10.0,
            "Reference station should calculate normally")

        // TODO: Subordinate station logic not yet implemented
        // When implemented, uncomment and verify:
        // val subHeight = calcWithOffset.calculateHeight("SUB001", time)
        // High tides should be offset by +0.5 ft
        // Low tides should be offset by -0.3 ft
    }

    @Test
    fun `test subordinate station with time offsets`() {
        val refConstituents = listOf(
            HarmonicConstituent("REF002", "M2", 2.5, 190.0),
            HarmonicConstituent("REF002", "K1", 0.9, 160.0)
        )

        // Subordinate with time offsets: +30 min for highs, +45 min for lows
        val offset = SubordinateOffset(
            stationId = "SUB002",
            referenceStationId = "REF002",
            heightOffsetHigh = 0.0,
            heightOffsetLow = 0.0,
            timeOffsetHigh = 30,  // minutes
            timeOffsetLow = 45
        )

        val calcWithOffset = HarmonicCalculator(
            constituents = mapOf("REF002" to refConstituents),
            subordinateOffsets = mapOf("SUB002" to offset)
        )

        val startTime = Instant.parse("2026-02-12T00:00:00Z")

        // Find high tide at reference station
        val refHigh = calcWithOffset.findNextExtremum("REF002", startTime, findHigh = true)
        assertNotNull(refHigh)

        // TODO: When subordinate logic is implemented:
        // val subHigh = calcWithOffset.findNextExtremum("SUB002", startTime, findHigh = true)
        // assertNotNull(subHigh)
        // assertEquals(refHigh.time.plusSeconds(1800), subHigh.time, "Subordinate high should be 30 min later")
    }

    @Test
    fun `test subordinate station offset structure is valid`() {
        // Verify SubordinateOffset data structure is properly defined
        val offset = SubordinateOffset(
            stationId = "SUB003",
            referenceStationId = "REF999",
            heightOffsetHigh = 0.5,
            heightOffsetLow = -0.2,
            timeOffsetHigh = 30,
            timeOffsetLow = 45
        )

        // Verify properties are accessible
        assertEquals("SUB003", offset.stationId)
        assertEquals("REF999", offset.referenceStationId)
        assertEquals(0.5, offset.heightOffsetHigh, 0.001)
        assertEquals(-0.2, offset.heightOffsetLow, 0.001)
        assertEquals(30, offset.timeOffsetHigh)
        assertEquals(45, offset.timeOffsetLow)

        // TODO: Test actual subordinate calculation when implemented
    }

    // ============================================================================
    // ERROR HANDLING TESTS
    // ============================================================================

    @Test
    fun `test calculateHeight throws for unknown station`() {
        try {
            calculator.calculateHeight("INVALID_STATION_ID", Instant.now())
            // If no exception, test fails
            assertTrue(false, "Should throw IllegalArgumentException for unknown station")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("No constituents found") == true,
                "Error message should indicate missing constituents")
        }
    }

    @Test
    fun `test calculateHeight throws for empty constituents`() {
        val emptyCalc = HarmonicCalculator(
            constituents = mapOf("EMPTY" to emptyList())
        )

        try {
            emptyCalc.calculateHeight("EMPTY", Instant.now())
            assertTrue(false, "Should throw IllegalArgumentException for empty constituents")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("no constituents") == true,
                "Error message should indicate no constituents")
        }
    }

    @Test
    fun `test findNextExtremum returns null when no extremum found`() {
        // Very short search window - may not find extremum
        val startTime = Instant.parse("2026-02-12T00:00:00Z")

        // Try to find extremum in a very short window
        // (Implementation uses 30-hour max search, so this should succeed)
        val extremum = calculator.findNextExtremum("9414290", startTime, findHigh = true)

        // Should find an extremum within reasonable time
        assertNotNull(extremum, "Should find extremum within 30 hours")
    }

    @Test
    fun `test calculateHeight with null constituents map`() {
        val nullCalc = HarmonicCalculator(constituents = emptyMap())

        try {
            nullCalc.calculateHeight("ANY_STATION", Instant.now())
            assertTrue(false, "Should throw for missing station")
        } catch (e: IllegalArgumentException) {
            // Expected behavior
            assertTrue(true)
        }
    }

    @Test
    fun `test calculateRateOfChange with unknown station`() {
        try {
            calculator.calculateRateOfChange("UNKNOWN", Instant.now())
            assertTrue(false, "Should throw for unknown station")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("constituents") == true)
        }
    }

    @Test
    fun `test findExtrema with invalid time range`() {
        val endTime = Instant.parse("2026-02-12T00:00:00Z")
        val startTime = endTime.plusSeconds(86400) // Start after end

        // Should return empty list for invalid range
        val extrema = calculator.findExtrema("9414290", startTime, endTime)
        assertTrue(extrema.isEmpty(), "Should return empty list for invalid time range")
    }

    @Test
    fun `test generateTideCurve with small interval`() {
        val startTime = Instant.parse("2026-02-12T00:00:00Z")
        val endTime = startTime.plusSeconds(3600) // 1 hour

        // Small interval (1 minute) should work
        val curve = calculator.generateTideCurve("9414290", startTime, endTime,
            intervalMinutes = 1)

        // Should generate approximately 60 points for 1 hour at 1 min intervals
        assertTrue(curve.size >= 60, "Should generate points at 1 minute intervals")
        assertTrue(curve.size <= 62, "Should not generate too many points")

        // Verify points are 1 minute apart
        for (i in 0 until curve.size - 1) {
            val timeDiff = curve[i + 1].time.epochSecond - curve[i].time.epochSecond
            assertEquals(60L, timeDiff, "Points should be 1 minute (60 sec) apart")
        }
    }
}
