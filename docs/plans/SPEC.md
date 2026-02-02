# TideWatch - WearOS Tide Prediction App Specification

## Project Overview

TideWatch is an offline-first, privacy-focused tide prediction app for WearOS smartwatches, designed for fishing and coastal use. The app performs real-time harmonic analysis using NOAA station data to predict tides without any network dependency after installation.

**Target Timeline:** MVP in 3-5 days

## Core Principles

1. **Offline-first**: Complete functionality without network after installation
2. **Privacy-first**: No tracking, analytics, or accounts. Minimal permissions (coarse location, optional)
3. **Open source**: GPL licensed, source available on GitHub
4. **Battery-aware**: Optimized for watch constraints through intelligent caching
5. **Trustworthy math**: NOAA-style harmonic analysis, validated against official predictions

## Architecture

### Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose for WearOS
- **Minimum SDK**: WearOS 3+ (API 30+)
- **Build System**: Gradle with Kotlin DSL
- **CI/CD**: GitHub Actions
- **Distribution**: Google Play Store

### High-Level Architecture

```
┌─────────────────────────────────────────┐
│          WearOS UI Layer                │
│  ┌──────────────┐    ┌──────────────┐  │
│  │  Watch App   │    │  Tile Widget │  │
│  └──────────────┘    └──────────────┘  │
└────────────┬─────────────────┬──────────┘
             │                 │
┌────────────┴─────────────────┴──────────┐
│       Tide Calculation Engine           │
│  ┌───────────────────────────────────┐  │
│  │  Harmonic Analysis Core           │  │
│  │  - 37+ tidal constituents         │  │
│  │  - Astronomical node factors      │  │
│  │  - Equilibrium arguments          │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │  Extrema Cache (high/low tides)   │  │
│  └───────────────────────────────────┘  │
└────────────┬────────────────────────────┘
             │
┌────────────┴────────────────────────────┐
│        Station Database                 │
│  ┌───────────────────────────────────┐  │
│  │  Bundled SQLite Database          │  │
│  │  - ~3,000 stations (5-7 MB)       │  │
│  │  - Primary + subordinate stations │  │
│  │  - Harmonic constants per station │  │
│  │  - Metadata (name, lat/lon, etc)  │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

## Data Layer

### Station Database Schema

**stations** table:
- `id` (TEXT, primary key) - NOAA station ID
- `name` (TEXT) - Human-readable name
- `state` (TEXT) - State/region
- `latitude` (REAL)
- `longitude` (REAL)
- `type` (TEXT) - "harmonic" or "subordinate"
- `timezone_offset` (INTEGER) - UTC offset in minutes (for display purposes)
- `reference_station_id` (TEXT, nullable) - For subordinate stations

**harmonic_constituents** table:
- `station_id` (TEXT, foreign key)
- `constituent_name` (TEXT) - e.g., "M2", "S2", "K1"
- `amplitude` (REAL) - in feet/meters
- `phase_local` (REAL) - in degrees

**subordinate_offsets** table:
- `station_id` (TEXT, foreign key)
- `reference_station_id` (TEXT)
- `time_offset_high` (INTEGER) - minutes
- `time_offset_low` (INTEGER) - minutes
- `height_offset_high` (REAL) - multiplier
- `height_offset_low` (REAL) - multiplier

### Data Pipeline

1. **Fetch**: Python script queries NOAA CO-OPS API for all stations
   - Endpoint: `https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json`
   - Get harmonic constituents for each station
   - Get subordinate station relationships

2. **Transform**: Convert JSON to SQLite database
   - Parse constituent amplitudes and phases
   - Store metadata (station names, coordinates)
   - Build spatial index for location-based search

3. **Bundle**: SQLite database included in APK assets
   - Database copied to app storage on first launch
   - Version tracked for future updates

4. **Automation**: GitHub Actions workflow
   - Runs monthly or on manual trigger
   - Fetches latest NOAA data
   - Rebuilds database
   - Creates PR with updated data if changes detected

## Calculation Engine

### Harmonic Analysis Implementation

**Core Algorithm**:
```
height(t) = datum + Σ(amplitude[i] × f[i] × cos(ω[i]×t + phase[i] - κ[i]))
```

Where:
- `datum` = MLLW (Mean Lower Low Water) for station
- `amplitude[i]` = constituent amplitude from database
- `f[i]` = node factor (calculated from astronomical cycles)
- `ω[i]` = constituent angular velocity (degrees/hour) - globally defined
- `phase[i]` = local phase from database
- `κ[i]` = equilibrium argument (calculated from astronomical position)

**Key Constituents** (37 standard NOAA constituents):
- Principal lunar: M2, N2, 2N2
- Principal solar: S2, K2
- Diurnal: K1, O1, P1, Q1
- Long period: Mm, Mf, Ssa
- Compound: M4, M6, MS4, etc.

### Calculation Strategy (Hybrid Caching)

**On station selection or date change:**
1. Calculate astronomical node factors for current year
2. Pre-compute high/low tide times for next 7 days
   - Use Newton's method to find extrema
   - Store times and heights in memory cache
   - Cache invalidated daily

**On UI update:**
1. Check cache for next high/low times (fast lookup)
2. Calculate current height using full harmonic sum
3. Calculate rate of change (derivative) for tide direction
4. If user requests detail view, calculate full tide curve (height every 10 minutes for 24 hours)

**For subordinate stations:**
1. Calculate reference station tide
2. Apply time offsets to high/low tide times
3. Apply height multipliers to tide heights
4. No caching difference from primary stations

### Performance Targets

- Station database load: <100ms
- Initial cache computation (7-day extrema): <200ms
- Current height calculation: <5ms
- Full 24-hour curve generation: <50ms
- Battery impact: <2% per hour with screen on

## Feature Specifications

### MVP Features (3-5 day target)

#### 1. Station Selection

**Location-based Search** (default):
- Request coarse location permission on first launch
- Find nearest 10 stations by coastline distance (not linear)
- **Coastline Distance Algorithm**:
  - Pre-compute stations within reasonable radius (50 miles)
  - For each candidate, check if great-circle path crosses land
  - If crosses land, penalize distance heavily (10x multiplier)
  - Sort by adjusted distance
  - Present top matches to user
- User selects from list using rotary input
- Store selected station(s) locally

**Manual Search**:
- Hierarchy: Region → State → Station
- Use rotary input for navigation
- Search filters down as user types (if keyboard available)
- Show station distance from current location if available

**Multi-Station Support**:
- User can save up to 10 favorite stations
- Quick switch between stations in app
- Each station maintains its own extrema cache

#### 2. Main App UI

**Primary Screen** (Compose for WearOS):
- **Header**: Station name (truncated if needed)
- **Current Tide Display**:
  - Large current height (e.g., "4.2 ft" or "1.3 m")
  - Tide direction indicator: ⬆ Rising / ⬇ Falling / ⟷ Slack
  - Rate of change (e.g., "+1.2 ft/hr")
- **Next Extrema**:
  - "High: 8:47 AM (6.8 ft)"
  - "Low: 2:34 PM (0.4 ft)"
- **Mini Timeline**: 24-hour visual curve (simple line graph)
- **Actions**: Rotary scroll for detail, tap for station picker

**Detail Screen** (scrollable):
- Full 24-hour tide curve graph
- List of all highs/lows for next 7 days
- Station metadata (last updated, MLLW datum)
- Optional: Display calculation metadata (node factors) for verification

**Settings Screen**:
- Unit preference (feet/meters)
- Timezone display preference (UTC vs watch timezone)
- Station management (add/remove favorites)
- About (version, data version, license)

#### 3. Tile Widget

**Compact Tile** (swipeable from watch face):
- Station name
- Current height + direction arrow
- Next high or low time
- Tap to launch full app
- Updates every 5 minutes or when screen turns on

#### 4. Always-On Display (AOD) Optimization

- Simplified rendering in AOD mode
- Show only current height and next extrema
- Reduce update frequency to every 15 minutes
- High contrast, minimal colors

### Future Features (Post-MVP)

- Watch face complications
- Multiple tile variants (compact/detailed)
- Notifications for optimal tide windows
- Tidal current stations (different calculation)
- Export tide data for planning
- Nearby station discovery map view
- Moon phase display (for fishing correlation)

## Testing Strategy

### Unit Testing
- **Harmonic calculation accuracy**: Compare against NOAA published predictions
  - Test cases: Multiple stations, various dates, different tidal regimes
  - Acceptable error: ±0.1 ft for height, ±2 minutes for times
  - Test framework: JUnit + Kotlin test

- **Astronomical calculations**: Node factors and equilibrium arguments
  - Cross-reference with published astronomical ephemeris
  - Test edge cases: leap years, year boundaries, century boundaries

- **Subordinate station offsets**: Verify time and height adjustments
  - Test with known reference/subordinate pairs
  - Verify offset interpolation for intermediate times

### Integration Testing
- Database queries and station selection
- Cache invalidation and refresh
- Multi-station management

### UI Testing
- Emulator testing for UI layout and interactions
- Manual testing on physical WearOS device (required for battery/performance)
- Test on multiple screen sizes (small/large watches)

### Validation
- Display calculation metadata in UI (node factors, constituent phases)
- Compare app predictions with official NOAA predictions in field
- Beta testing with fishing community for real-world feedback

## Technical Implementation Details

### Project Structure
```
tidewatch/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/
│   │   │   │   ├── com/tidewatch/
│   │   │   │   │   ├── data/
│   │   │   │   │   │   ├── StationRepository.kt
│   │   │   │   │   │   ├── TideDatabase.kt
│   │   │   │   │   │   └── models/
│   │   │   │   │   ├── tide/
│   │   │   │   │   │   ├── HarmonicCalculator.kt
│   │   │   │   │   │   ├── AstronomicalCalculator.kt
│   │   │   │   │   │   ├── TideCache.kt
│   │   │   │   │   │   └── Constituents.kt
│   │   │   │   │   ├── ui/
│   │   │   │   │   │   ├── app/
│   │   │   │   │   │   ├── tile/
│   │   │   │   │   │   ├── components/
│   │   │   │   │   │   └── theme/
│   │   │   │   │   └── TideWatchApp.kt
│   │   │   ├── assets/
│   │   │   │   └── tides.db (SQLite database)
│   │   │   └── res/
│   │   └── test/
│   │       └── kotlin/
│   │           └── com/tidewatch/
│   │               ├── tide/
│   │               │   ├── HarmonicCalculatorTest.kt
│   │               │   └── validation/
│   │               │       └── NOAAComparisonTest.kt
│   └── build.gradle.kts
├── tools/
│   └── data-pipeline/
│       ├── fetch_noaa_data.py
│       ├── build_database.py
│       └── requirements.txt
├── .github/
│   └── workflows/
│       ├── ci.yml
│       ├── data-update.yml
│       └── release.yml
├── docs/
│   ├── plans/
│   │   └── SPEC.md (this document)
│   ├── ADR/ (Architecture Decision Records)
│   └── CONTRIBUTING.md
├── README.md
├── LICENSE (GPL-3.0)
└── build.gradle.kts
```

### Key Modules

#### 1. Data Layer (`data/`)
- **StationRepository**: CRUD operations for stations, query by location
- **TideDatabase**: Room database wrapper (or direct SQLite)
- **Models**: Station, HarmonicConstituent, SubordinateOffset data classes

#### 2. Tide Calculation (`tide/`)
- **HarmonicCalculator**: Core harmonic analysis implementation
  - `calculateHeight(station, time): Float`
  - `findNextExtremum(station, startTime, findHigh): TideExtremum`
  - `calculateRateOfChange(station, time): Float`

- **AstronomicalCalculator**: Node factors and equilibrium arguments
  - `calculateNodeFactor(constituent, year): Float`
  - `calculateEquilibriumArgument(constituent, time): Float`

- **TideCache**: In-memory cache for extrema
  - `getNextHigh(station, fromTime): TideExtremum?`
  - `getNextLow(station, fromTime): TideExtremum?`
  - `invalidate(station)`

- **Constituents**: Constants for all 37 NOAA constituents
  - Angular velocities (ω)
  - Human-readable names and descriptions

#### 3. UI Layer (`ui/`)
- **app/**: Main watch app screens (Compose)
  - `TideMainScreen`: Primary tide display
  - `TideDetailScreen`: Full curve and 7-day list
  - `StationPickerScreen`: Station selection
  - `SettingsScreen`: App preferences

- **tile/**: Tile service and composables
  - `TideTileService`: WearOS tile provider
  - `TideCompactTile`: Compact tile layout

- **components/**: Reusable UI components
  - `TideGraph`: 24-hour curve chart
  - `TideDirectionIndicator`: Arrow with rate
  - `StationList`: Scrollable station picker

- **theme/**: Material You theming
  - Dynamic color scheme
  - Typography for small screens
  - High contrast for outdoor readability

### Build Configuration

**build.gradle.kts** (app module):
```kotlin
android {
    namespace = "com.tidewatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tidewatch"
        minSdk = 30 // WearOS 3+
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
}

dependencies {
    // WearOS + Compose
    implementation("androidx.wear.compose:compose-material:1.2.0")
    implementation("androidx.wear.compose:compose-foundation:1.2.0")
    implementation("androidx.wear.tiles:tiles:1.2.0")

    // Room for database (or direct SQLite)
    implementation("androidx.room:room-runtime:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

### GitHub Actions Workflows

**ci.yml** (Run on every push):
```yaml
name: CI
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: 17
      - name: Run unit tests
        run: ./gradlew test
      - name: Run lint
        run: ./gradlew lint
```

**data-update.yml** (Monthly or manual):
```yaml
name: Update NOAA Data
on:
  schedule:
    - cron: '0 0 1 * *' # Monthly
  workflow_dispatch:
jobs:
  update-data:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-python@v4
        with:
          python-version: '3.11'
      - name: Fetch NOAA data
        run: |
          cd tools/data-pipeline
          pip install -r requirements.txt
          python fetch_noaa_data.py
          python build_database.py
      - name: Create PR if changed
        uses: peter-evans/create-pull-request@v5
        with:
          title: 'chore: update NOAA harmonic constants'
```

**release.yml** (On git tag):
```yaml
name: Release
on:
  push:
    tags:
      - 'v*'
jobs:
  build-release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build release APK
        run: ./gradlew assembleRelease
      - name: Sign APK
        # ... signing steps
      - name: Create GitHub release
        uses: softprops/action-gh-release@v1
        with:
          files: app/build/outputs/apk/release/*.apk
```

## Data Pipeline Details

### NOAA CO-OPS API Integration

**fetch_noaa_data.py**:
```python
import requests
import json

# Fetch all stations
stations_url = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json"
params = {"type": "tidepredictions", "expand": "constituents"}
response = requests.get(stations_url, params=params)
stations = response.json()["stations"]

# For each station, fetch harmonic constituents
for station in stations:
    station_id = station["id"]
    harmonics_url = f"https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations/{station_id}/harcon.json"
    # ... fetch and parse constituents
```

**build_database.py**:
```python
import sqlite3
import json

# Create database schema
conn = sqlite3.connect("tides.db")
cursor = conn.cursor()

# Create tables (see schema above)
# Insert station data
# Insert harmonic constituents
# Build spatial index for location queries
```

### Database Size Estimation
- ~3,000 stations
- Average 37 constituents per station
- Metadata ~500 bytes per station
- Constituents ~20 bytes each
- **Total**: ~4-6 MB compressed

## Addressing Key Concerns

### 1. Battery Life Optimization
- **Hybrid caching**: Pre-compute extrema once, calculate current height on-demand
- **Update throttling**: Only recalculate when screen is on or tile updates
- **Efficient algorithms**: Use Newton's method for extrema finding (converges in 3-5 iterations)
- **AOD mode**: Reduce update frequency to 15 minutes
- **Profile**: Use Android Profiler to identify battery hotspots

### 2. UX on Tiny Screen
- **Information hierarchy**: Most important data (current height, next high/low) prominently displayed
- **Progressive disclosure**: Main screen shows essentials, scroll for details
- **High contrast**: Material You theming with high contrast mode for outdoor visibility
- **Large touch targets**: Follow WearOS guidelines (48dp minimum)
- **Rotary input**: Primary navigation method, natural for watch interaction

### 3. Nearest Station by Coastline
**Algorithm**:
1. Query stations within 100-mile radius using spatial index
2. For each candidate station:
   - Calculate great-circle distance to user
   - Sample 10 points along great-circle path
   - For each point, check if over land using coastline dataset (optional) or simple heuristic
   - If any point over land, apply penalty: `adjusted_distance = distance × 10`
3. Sort by adjusted distance
4. Present top 10 stations

**Simplification for MVP**: Skip coastline detection initially. Instead:
- Use state/region filtering (if in Oregon, prioritize Oregon stations)
- Rely on station metadata (bay vs ocean stations often marked)
- Allow manual override in station picker

**Future Enhancement**: Bundle simplified coastline shapefile or use online service

## Open Questions / Future Considerations

1. **Daylight Saving Time**: Current approach (UTC calculation, watch timezone display) may confuse users when DST changes. Consider adding explicit DST handling or clearer UI indication.

2. **Great Lakes**: NOAA has "tide" stations but they're seiche (wind-driven), not astronomical. Harmonic model doesn't apply well. Consider filtering these stations or adding warning.

3. **Tidal Currents**: NOAA has separate current predictions (velocity, not height). Different calculation method. Future feature.

4. **Offline Maps**: Station picker could benefit from offline map view. Large data size concern.

5. **Complications**: Watch face complications require different API and very limited space. Post-MVP.

6. **Historical Data**: Should app show past tides? Useful for logging fishing trips but adds complexity.

7. **Multi-Day Planning**: Fishermen plan trips days in advance. Consider adding calendar view of optimal tide windows.

## References

- [NOAA CO-OPS API Documentation](https://api.tidesandcurrents.noaa.gov/api/prod/)
- [Harmonic Analysis of Tides (NOAA Tech Report)](https://tidesandcurrents.noaa.gov/publications/glossary2.pdf)
- [WearOS Design Guidelines](https://developer.android.com/training/wearables/design)
- [Jetpack Compose for WearOS](https://developer.android.com/training/wearables/compose)

## License

- **App Code**: GPL-3.0 (open source)
- **NOAA Data**: Public domain (US Government work)
- **NOAA Logo/Branding**: Cannot use official NOAA branding without permission

## Development Roadmap

### Day 1: Foundation
- Set up project structure and build configuration
- Implement basic data pipeline (fetch sample NOAA data)
- Create database schema and repository layer
- Start harmonic calculation engine (core algorithm)

### Day 2: Core Calculations
- Complete harmonic calculation engine
- Implement astronomical calculations (node factors, equilibrium arguments)
- Write unit tests against NOAA predictions
- Add extrema finding (Newton's method)

### Day 3: Basic UI
- Build simple station selection UI (manual search to start)
- Implement main app screen with current tide display
- Add tide cache for extrema
- Test on emulator

### Day 4: Complete MVP Features
- Add location-based station search
- Create tile widget
- Implement detail screen (tide curve, 7-day list)
- Basic settings screen

### Day 5: Polish & Testing
- Material You theming
- AOD optimization
- Battery profiling
- Validation against NOAA predictions
- Bug fixes

### Post-MVP
- Data pipeline automation (GitHub Actions)
- Full subordinate station support
- Improved coastline distance algorithm
- Additional features from future list

---

**Next Steps**: Set up Android Studio project with WearOS template. Configure Gradle build files. Begin implementing data pipeline to fetch NOAA harmonic constants.
