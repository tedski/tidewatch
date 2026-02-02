package com.tidewatch.tide

import com.tidewatch.data.models.TideExtremum
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * In-memory cache for pre-computed tide extrema (high/low tides).
 *
 * The cache stores 7 days of extrema for each station to avoid recalculating
 * them on every UI update. This is a key battery optimization.
 *
 * Cache is automatically invalidated at midnight (daily refresh).
 *
 * @property calculator The harmonic calculator to use for generating extrema
 */
class TideCache(
    private val calculator: HarmonicCalculator
) {
    /**
     * Cache storage: Map of station ID to cached extrema data.
     */
    private val cache = mutableMapOf<String, CachedExtrema>()

    /**
     * Mutex for thread-safe cache access.
     */
    private val mutex = Mutex()

    /**
     * Cached extrema data for a single station.
     */
    private data class CachedExtrema(
        val extrema: List<TideExtremum>,
        val cacheDate: LocalDate, // Date when cache was created
        val startTime: Instant,
        val endTime: Instant
    ) {
        /**
         * Returns true if this cache is still valid (not expired).
         */
        fun isValid(): Boolean {
            val today = LocalDate.now(ZoneOffset.UTC)
            return cacheDate == today
        }
    }

    /**
     * Get the next high tide after a given time.
     *
     * @param stationId Station identifier
     * @param fromTime UTC time to search from
     * @return Next high tide, or null if not found in cache
     */
    suspend fun getNextHigh(stationId: String, fromTime: Instant): TideExtremum? {
        return getNextExtremum(stationId, fromTime, isHigh = true)
    }

    /**
     * Get the next low tide after a given time.
     *
     * @param stationId Station identifier
     * @param fromTime UTC time to search from
     * @return Next low tide, or null if not found in cache
     */
    suspend fun getNextLow(stationId: String, fromTime: Instant): TideExtremum? {
        return getNextExtremum(stationId, fromTime, isHigh = false)
    }

    /**
     * Get the next extremum (high or low) after a given time.
     *
     * @param stationId Station identifier
     * @param fromTime UTC time to search from
     * @param isHigh If true, search for high tide; if false, search for low tide
     * @return Next extremum, or null if not found in cache
     */
    private suspend fun getNextExtremum(
        stationId: String,
        fromTime: Instant,
        isHigh: Boolean
    ): TideExtremum? {
        val cached = getOrCreateCache(stationId)
        val targetType = if (isHigh) TideExtremum.Type.HIGH else TideExtremum.Type.LOW

        return cached.extrema
            .filter { it.type == targetType }
            .firstOrNull { it.time.isAfter(fromTime) }
    }

    /**
     * Get all extrema for a station within a time range.
     *
     * @param stationId Station identifier
     * @param startTime Start of range (UTC)
     * @param endTime End of range (UTC)
     * @return List of extrema within the range, sorted by time
     */
    suspend fun getExtremaInRange(
        stationId: String,
        startTime: Instant,
        endTime: Instant
    ): List<TideExtremum> {
        val cached = getOrCreateCache(stationId)

        return cached.extrema.filter {
            !it.time.isBefore(startTime) && !it.time.isAfter(endTime)
        }
    }

    /**
     * Get all cached extrema for a station.
     *
     * @param stationId Station identifier
     * @return List of all cached extrema, sorted by time
     */
    suspend fun getAllExtrema(stationId: String): List<TideExtremum> {
        val cached = getOrCreateCache(stationId)
        return cached.extrema
    }

    /**
     * Invalidate cache for a specific station.
     *
     * @param stationId Station identifier
     */
    suspend fun invalidate(stationId: String) {
        mutex.withLock {
            cache.remove(stationId)
        }
    }

    /**
     * Invalidate all caches.
     */
    suspend fun invalidateAll() {
        mutex.withLock {
            cache.clear()
        }
    }

    /**
     * Invalidate expired caches (those created on a previous day).
     *
     * Should be called periodically (e.g., on app startup or at midnight).
     */
    suspend fun invalidateExpired() {
        mutex.withLock {
            val iterator = cache.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!entry.value.isValid()) {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Get cached extrema for a station, creating it if necessary.
     *
     * @param stationId Station identifier
     * @return Cached extrema data
     */
    private suspend fun getOrCreateCache(stationId: String): CachedExtrema {
        mutex.withLock {
            // Check if cache exists and is valid
            val existing = cache[stationId]
            if (existing != null && existing.isValid()) {
                return existing
            }

            // Generate new cache
            val now = Instant.now()
            val startTime = now.truncatedTo(ChronoUnit.DAYS) // Start of today (UTC)
            val endTime = startTime.plus(7, ChronoUnit.DAYS) // 7 days ahead

            val extrema = calculator.findExtrema(stationId, startTime, endTime)

            val cached = CachedExtrema(
                extrema = extrema,
                cacheDate = LocalDate.now(ZoneOffset.UTC),
                startTime = startTime,
                endTime = endTime
            )

            cache[stationId] = cached
            return cached
        }
    }

    /**
     * Pre-warm the cache for a station.
     *
     * Useful to call in the background when a station is selected,
     * so the cache is ready when the UI needs it.
     *
     * @param stationId Station identifier
     */
    suspend fun prewarm(stationId: String) {
        getOrCreateCache(stationId)
    }

    /**
     * Get cache statistics for monitoring.
     *
     * @return Map of station ID to cache info
     */
    suspend fun getCacheStats(): Map<String, CacheStats> {
        return mutex.withLock {
            cache.mapValues { (_, cached) ->
                CacheStats(
                    extremaCount = cached.extrema.size,
                    cacheDate = cached.cacheDate,
                    isValid = cached.isValid(),
                    startTime = cached.startTime,
                    endTime = cached.endTime
                )
            }
        }
    }

    /**
     * Cache statistics for a single station.
     */
    data class CacheStats(
        val extremaCount: Int,
        val cacheDate: LocalDate,
        val isValid: Boolean,
        val startTime: Instant,
        val endTime: Instant
    )
}
