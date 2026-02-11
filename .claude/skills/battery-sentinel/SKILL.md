---
name: battery-sentinel
description: Evaluate battery impact of proposed features to maintain the strict <2% per hour target for the TideWatch WearOS app
user-invocable: true
---

# Battery Sentinel

**Invoke with**: `/battery-sentinel [feature description]`

## Purpose

Evaluate battery impact of proposed features to maintain the strict <2% per hour target for the TideWatch WearOS app.

## Baseline Patterns (Current Codebase)

### Caching Strategy
- **7-day extrema pre-computation**: One-time ~200ms calculation per day
- **On-demand height calculation**: Real-time computation only when needed
- **In-memory caching**: Frequently accessed data (e.g., current station)

### Update Intervals
- **Active UI**: 1-minute refresh for current tide data
- **AOD mode**: 15-minute updates (reduced frequency)
- **No background updates**: App only active when visible

### Coroutine Management
```kotlin
// ViewModelScope with automatic cancellation
viewModelScope.launch {
    // Work cancelled when ViewModel cleared
}

// Lazy initialization
private val calculator by lazy { HarmonicCalculator() }
```

### Architectural Principles
- **Offline-first**: Zero network requests (all data bundled)
- **No sensors**: No GPS, accelerometer, or other sensor polling
- **Smart recomposition**: Use `remember`, stable keys, derived state
- **Main thread safety**: Heavy computation in background coroutines

### AOD Optimization
- Reduced update frequency (15 min vs 1 min)
- Simplified UI rendering
- Minimal recomposition

## Warning Triggers

⚠️ **High-Frequency Updates**: Refresh intervals <60 seconds for non-critical data
⚠️ **Network Activity**: Any network requests (violates offline-first architecture)
⚠️ **Sensor Polling**: Continuous sensor access (GPS, accelerometer, etc.)
⚠️ **Uncancelled Coroutines**: Background work without lifecycle management
⚠️ **Wake Locks**: Wake locks without timeout or clear necessity
⚠️ **Main Thread Blocking**: Heavy computation not dispatched to background
⚠️ **Missing AOD Support**: Always-visible features without ambient optimization
⚠️ **Recomposition Issues**: Missing `remember`, unstable keys, unnecessary recomposition
⚠️ **Inefficient Queries**: Database queries in hot paths without caching

## Checking Process

When a feature is proposed or implemented:

1. **Identify Update Pattern**
   - What data refreshes? At what frequency?
   - Is it continuous or triggered by user action?
   - Does it respect AOD mode?

2. **Analyze Resource Usage**
   - Network requests: YES/NO (should be NO)
   - Sensor usage: Type and polling frequency
   - Database queries: Frequency and complexity
   - Computation: O(n) complexity and typical n value

3. **Review Lifecycle Management**
   - Are coroutines scoped to ViewModel/Composable?
   - Do timers/listeners get cancelled?
   - Is there cleanup in onCleared/DisposableEffect?

4. **Estimate Battery Impact**
   ```
   Impact = (computation_time_ms × frequency_per_hour × power_factor) / 3600000
   Target: <2% per hour total app usage
   ```
   - Quick calculation (~10ms): Negligible
   - Medium (50-200ms): Monitor frequency
   - Heavy (>500ms): Needs caching/optimization

5. **Check AOD Optimization**
   - Does feature respect ambient mode?
   - Are updates reduced in AOD?
   - Is UI simplified for always-on display?

## Output Format

```markdown
## Battery Sentinel Analysis

### ✅ Compliant Patterns
- [List of battery-efficient patterns being followed]

### ⚠️ Warnings
- **Issue**: [Description of battery concern]
  - **Pattern**: [Current app strategy]
  - **Proposed**: [What's being introduced]
  - **Impact**: [Estimated battery cost]
  - **Recommendation**: [How to optimize]

### 📊 Impact Summary
**Estimated battery cost**: [X%/hour or "Negligible" or "Needs optimization"]
**Within <2% target**: YES/NO
**Next steps**: [Recommendations]
```

## Example Analysis

**Feature**: "Add live wave height overlay from NOAA API"

### ✅ Compliant Patterns
- (None - violates offline-first architecture)

### ⚠️ Warnings
- **Issue**: Network requests proposed
  - **Pattern**: App is 100% offline with bundled database
  - **Proposed**: Periodic API calls to NOAA for wave data
  - **Impact**: Network radio wake = ~200-400mW, 1-2% battery per request
  - **Recommendation**: Either (1) bundle wave data like tide data, or (2) make feature explicitly opt-in with clear battery warning

- **Issue**: Update frequency unclear
  - **Pattern**: Current data refreshes every 1 minute (local calculation)
  - **Proposed**: Unknown refresh interval for API calls
  - **Impact**: If every 5 min = ~12 requests/hour = 12-24% battery (exceeds target by 10x)
  - **Recommendation**: If network required, use 30-60 min intervals minimum + cache responses

### 📊 Impact Summary
**Estimated battery cost**: 10-20%/hour (unacceptable)
**Within <2% target**: NO
**Next steps**: Redesign as offline-first or make opt-in with hourly updates
