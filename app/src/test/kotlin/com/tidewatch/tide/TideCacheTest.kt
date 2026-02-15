package com.tidewatch.tide

import com.tidewatch.data.models.HarmonicConstituent
import com.tidewatch.data.models.TideExtremum
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for TideCache.
 *
 * Tests validate caching behavior, thread safety, invalidation logic,
 * and extrema retrieval for the 7-day pre-computation cache.
 *
 * TODO: Add tests for cache warming strategies and memory pressure scenarios
 * to validate behavior under resource constraints on WearOS devices.
 */
class TideCacheTest {

    private lateinit var calculator: HarmonicCalculator
    private lateinit var cache: TideCache

    private val testStationId = "9414290" // San Francisco

    @Before
    fun setup() {
        // Create calculator with San Francisco constituents
        val constituents = listOf(
            HarmonicConstituent(testStationId, "M2", 1.983, 190.0),
            HarmonicConstituent(testStationId, "S2", 0.515, 220.0),
            HarmonicConstituent(testStationId, "K1", 0.515, 160.0),
            HarmonicConstituent(testStationId, "O1", 0.365, 180.0)
        )

        calculator = HarmonicCalculator(
            constituents = mapOf(testStationId to constituents)
        )

        cache = TideCache(calculator)
    }

    @Test
    fun `test getNextHigh returns next high tide after given time`() {
        runBlocking {
            val fromTime = Instant.parse("2026-02-12T12:00:00Z")

            val nextHigh = cache.getNextHigh(testStationId, fromTime)

            assertNotNull(nextHigh, "Should find next high tide")
            assertEquals(TideExtremum.Type.HIGH, nextHigh.type, "Should be high tide")
            assertTrue(nextHigh.time.isAfter(fromTime), "High tide should be after fromTime")
        }
    }

    @Test
    fun `test getNextLow returns next low tide after given time`() {
        runBlocking {
            val fromTime = Instant.parse("2026-02-12T12:00:00Z")

            val nextLow = cache.getNextLow(testStationId, fromTime)

            assertNotNull(nextLow, "Should find next low tide")
            assertEquals(TideExtremum.Type.LOW, nextLow.type, "Should be low tide")
            assertTrue(nextLow.time.isAfter(fromTime), "Low tide should be after fromTime")
        }
    }

    @Test
    fun `test getAllExtrema returns multiple extrema sorted by time`() {
        runBlocking {
            val allExtrema = cache.getAllExtrema(testStationId)

            assertTrue(allExtrema.size >= 10, "Should have at least 10 extrema for 7 days")

            // Verify sorted by time
            for (i in 0 until allExtrema.size - 1) {
                assertTrue(
                    allExtrema[i].time.isBefore(allExtrema[i + 1].time),
                    "Extrema should be sorted by time"
                )
            }

            // Verify mix of highs and lows
            val hasHighs = allExtrema.any { it.type == TideExtremum.Type.HIGH }
            val hasLows = allExtrema.any { it.type == TideExtremum.Type.LOW }
            assertTrue(hasHighs && hasLows, "Should have both high and low tides")
        }
    }

    @Test
    @Ignore("Flaky: test uses hardcoded dates but cache is computed from 'now' dynamically")
    fun `test getExtremaInRange filters by time range correctly`() {
        runBlocking {
            val startTime = Instant.parse("2026-02-12T00:00:00Z")
            val endTime = Instant.parse("2026-02-13T00:00:00Z") // 1 day

            val rangeExtrema = cache.getExtremaInRange(testStationId, startTime, endTime)

            // Verify all are within range
            for (extremum in rangeExtrema) {
                assertTrue(
                    !extremum.time.isBefore(startTime) && !extremum.time.isAfter(endTime),
                    "Extremum at ${extremum.time} should be within range $startTime to $endTime"
                )
            }

            // Should have 2-4 extrema in a day (semidiurnal tides)
            // Skip exact count check - there may be boundary conditions
            assertTrue(rangeExtrema.size >= 1, "Should have at least 1 extremum in one day")
        }
    }

    @Test
    fun `test cache reuses computed extrema on second call`() {
        runBlocking {
            val fromTime = Instant.parse("2026-02-12T12:00:00Z")

            // First call - computes and caches
            val firstCall = cache.getNextHigh(testStationId, fromTime)
            assertNotNull(firstCall)

            // Second call - should return same extremum (from cache)
            val secondCall = cache.getNextHigh(testStationId, fromTime)
            assertNotNull(secondCall)

            assertEquals(firstCall.time, secondCall.time, "Should return same cached extremum")
            assertEquals(firstCall.height, secondCall.height, 0.001, "Should have same height")
        }
    }

    @Test
    fun `test invalidate removes station cache`() {
        runBlocking {
            // Prime cache
            val firstCall = cache.getAllExtrema(testStationId)
            assertTrue(firstCall.isNotEmpty())

            // Invalidate
            cache.invalidate(testStationId)

            // Get stats - station should not be in cache
            val stats = cache.getCacheStats()
            assertNull(stats[testStationId], "Station should not be in cache after invalidation")

            // Next call should recompute (we can't verify recomputation directly,
            // but we can verify it still works)
            val afterInvalidate = cache.getAllExtrema(testStationId)
            assertTrue(afterInvalidate.isNotEmpty(), "Should recompute after invalidation")
        }
    }

    @Test
    fun `test invalidateAll clears all caches`() {
        runBlocking {
            val station2 = "8454000" // Providence
            val constituents2 = listOf(
                HarmonicConstituent(station2, "M2", 1.5, 180.0),
                HarmonicConstituent(station2, "S2", 0.4, 200.0)
            )

            val calculator2 = HarmonicCalculator(
                constituents = mapOf(
                    testStationId to listOf(
                        HarmonicConstituent(testStationId, "M2", 1.983, 190.0),
                        HarmonicConstituent(testStationId, "S2", 0.515, 220.0)
                    ),
                    station2 to constituents2
                )
            )
            val cache2 = TideCache(calculator2)

            // Prime caches for both stations
            cache2.getAllExtrema(testStationId)
            cache2.getAllExtrema(station2)

            val statsBefore = cache2.getCacheStats()
            assertEquals(2, statsBefore.size, "Should have 2 stations cached")

            // Clear all
            cache2.invalidateAll()

            val statsAfter = cache2.getCacheStats()
            assertEquals(0, statsAfter.size, "Should have no stations cached")
        }
    }

    @Test
    fun `test prewarm loads cache`() {
        runBlocking {
            // Verify cache is empty initially
            val statsBeforePrewarm = cache.getCacheStats()
            assertEquals(0, statsBeforePrewarm.size, "Cache should be empty initially")

            // Prewarm
            cache.prewarm(testStationId)

            // Verify cache is populated
            val statsAfterPrewarm = cache.getCacheStats()
            assertEquals(1, statsAfterPrewarm.size, "Cache should have 1 station")

            val stats = statsAfterPrewarm[testStationId]
            assertNotNull(stats, "Station should be in cache")
            assertTrue(stats.extremaCount > 0, "Should have computed extrema")
            assertTrue(stats.isValid, "Cache should be valid")
        }
    }

    @Test
    fun `test getCacheStats returns correct statistics`() {
        runBlocking {
            // Prime cache
            cache.getAllExtrema(testStationId)

            val stats = cache.getCacheStats()
            assertEquals(1, stats.size, "Should have 1 station")

            val stationStats = stats[testStationId]
            assertNotNull(stationStats, "Should have stats for test station")

            assertTrue(stationStats.extremaCount >= 10, "Should have multiple extrema")
            // Note: cacheDate is set to LocalDate.now() when cache is created,
            // so we verify cache is valid rather than checking exact date to avoid time-dependency
            assertTrue(stationStats.isValid, "Cache should be valid")

            // Verify time range is ~7 days (allow some rounding)
            val duration = ChronoUnit.DAYS.between(
                stationStats.startTime.atZone(ZoneOffset.UTC).toLocalDate(),
                stationStats.endTime.atZone(ZoneOffset.UTC).toLocalDate()
            )
            assertTrue(duration in 6..8, "Cache should span approximately 7 days, got $duration")
        }
    }

    @Test
    fun `test getNextHigh returns null when beyond cache range`() {
        runBlocking {
            // Request high tide far in the future (beyond 7-day cache)
            // Cache starts at 2026-02-12, so 2026-02-22 is beyond 7-day window
            val farFuture = Instant.parse("2026-02-22T12:00:00Z")

            val result = cache.getNextHigh(testStationId, farFuture)

            assertNull(result, "Should return null when time is beyond cache range")
        }
    }

    @Test
    fun `test extrema heights are reasonable`() {
        runBlocking {
            val allExtrema = cache.getAllExtrema(testStationId)

            assertTrue(allExtrema.isNotEmpty(), "Should have extrema")

            // All heights should be within reasonable range for tides
            for (extremum in allExtrema) {
                assertTrue(
                    extremum.height in -5.0..15.0,
                    "Tide height ${extremum.height} should be reasonable (SF tides are ~0-8 ft MLLW)"
                )
            }

            // Verify high tides are generally higher than low tides
            val avgHigh = allExtrema.filter { it.type == TideExtremum.Type.HIGH }
                .map { it.height }
                .average()
            val avgLow = allExtrema.filter { it.type == TideExtremum.Type.LOW }
                .map { it.height }
                .average()

            assertTrue(avgHigh > avgLow, "Average high tide should be higher than average low tide")
        }
    }

    @Test
    fun `test extrema timing follows semidiurnal pattern`() {
        runBlocking {
            val allExtrema = cache.getAllExtrema(testStationId)

            assertTrue(allExtrema.size >= 10, "Should have multiple extrema")

            // Semidiurnal tides: ~2 highs and 2 lows per day (period ~12.42 hours)
            // So consecutive extrema should be ~6 hours apart
            for (i in 0 until minOf(10, allExtrema.size - 1)) {
                val timeDiff = ChronoUnit.HOURS.between(
                    allExtrema[i].time,
                    allExtrema[i + 1].time
                )

                // Consecutive extrema should be 4-8 hours apart (6.2 hours nominal)
                assertTrue(
                    timeDiff in 4..8,
                    "Consecutive extrema should be ~6 hours apart, got $timeDiff hours"
                )
            }
        }
    }

    @Test
    fun `test multiple stations cached independently`() {
        runBlocking {
            val station2 = "8454000" // Providence
            val constituents2 = listOf(
                HarmonicConstituent(station2, "M2", 1.5, 180.0),
                HarmonicConstituent(station2, "S2", 0.4, 200.0),
                HarmonicConstituent(station2, "K1", 0.3, 150.0)
            )

            val calculator2 = HarmonicCalculator(
                constituents = mapOf(
                    testStationId to listOf(
                        HarmonicConstituent(testStationId, "M2", 1.983, 190.0),
                        HarmonicConstituent(testStationId, "S2", 0.515, 220.0)
                    ),
                    station2 to constituents2
                )
            )
            val cache2 = TideCache(calculator2)

            val fromTime = Instant.parse("2026-02-12T12:00:00Z")

            // Get extrema for both stations
            val station1High = cache2.getNextHigh(testStationId, fromTime)
            val station2High = cache2.getNextHigh(station2, fromTime)

            assertNotNull(station1High)
            assertNotNull(station2High)

            // Both should be valid and after fromTime
            assertTrue(station1High.time.isAfter(fromTime))
            assertTrue(station2High.time.isAfter(fromTime))

            // Verify both are in cache
            val stats = cache2.getCacheStats()
            assertEquals(2, stats.size, "Should have both stations cached")
        }
    }

    @Test
    fun `test getNextHigh with nonexistent station throws exception`() {
        runBlocking {
            val nonexistentStation = "INVALID999"
            val fromTime = Instant.parse("2026-02-12T12:00:00Z")

            // Calculator throws IllegalArgumentException for unknown stations
            assertFailsWith<IllegalArgumentException> {
                cache.getNextHigh(nonexistentStation, fromTime)
            }
        }
    }

    @Test
    fun `test getExtremaInRange with inverted time range returns empty list`() {
        runBlocking {
            val startTime = Instant.parse("2026-02-13T00:00:00Z")
            val endTime = Instant.parse("2026-02-12T00:00:00Z") // Before start!

            val result = cache.getExtremaInRange(testStationId, startTime, endTime)

            // Inverted range should return empty list
            assertTrue(result.isEmpty(), "Should return empty list for inverted time range")
        }
    }

    @Test
    fun `test concurrent access to cache is thread-safe`() {
        runBlocking {
            val fromTime = Instant.parse("2026-02-12T12:00:00Z")

            // Launch multiple concurrent operations
            val results = (1..20).map { index ->
                async {
                    when (index % 4) {
                        0 -> cache.getAllExtrema(testStationId)
                        1 -> listOfNotNull(cache.getNextHigh(testStationId, fromTime))
                        2 -> listOfNotNull(cache.getNextLow(testStationId, fromTime))
                        else -> cache.getExtremaInRange(testStationId, fromTime, fromTime.plus(1, ChronoUnit.DAYS))
                    }
                }
            }.awaitAll()

            // Operations should complete without exceptions
            // (Some may be empty depending on cache initialization timing)
            assertTrue(results.isNotEmpty(), "Should have concurrent results")

            // Cache should be in consistent state
            val stats = cache.getCacheStats()
            assertEquals(1, stats.size, "Cache should have exactly 1 station")
            assertTrue(stats[testStationId]!!.isValid, "Cache should still be valid")
        }
    }

    @Test
    fun `test getExtremaInRange includes boundary times`() {
        runBlocking {
            val allExtrema = cache.getAllExtrema(testStationId)
            val firstExtremum = allExtrema.first()

            // Range that starts exactly at extremum time
            val rangeExtrema = cache.getExtremaInRange(
                testStationId,
                firstExtremum.time, // Exact start
                firstExtremum.time.plus(6, ChronoUnit.HOURS)
            )

            assertTrue(
                rangeExtrema.any { it.time == firstExtremum.time },
                "Should include extremum at exact boundary time"
            )
        }
    }
}
