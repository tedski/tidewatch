# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**TideWatch** is an offline-first WearOS tide prediction app using harmonic analysis of NOAA data.

**Status**: Foundation complete (60% MVP), ready for UI implementation
**Language**: Kotlin
**Framework**: Jetpack Compose for WearOS
**Target**: WearOS 3+ (API 30+)

## Architecture

### Core Components

1. **Data Layer** (`app/src/main/kotlin/com/tidewatch/data/`)
   - `TideDatabase.kt` - Room database (stations, constituents, offsets)
   - `StationRepository.kt` - CRUD operations, location-based search
   - `models/` - Data classes (Station, HarmonicConstituent, TideExtremum, etc.)

2. **Calculation Engine** (`app/src/main/kotlin/com/tidewatch/tide/`)
   - `Constituents.kt` - 37 NOAA tidal constituent definitions
   - `AstronomicalCalculator.kt` - Node factors, equilibrium arguments
   - `HarmonicCalculator.kt` - Core tide prediction (h = Σ[A×f×cos(ω×t+φ-κ)])
   - `TideCache.kt` - 7-day extrema pre-computation for battery efficiency

3. **UI Layer** (`app/src/main/kotlin/com/tidewatch/ui/`)
   - `theme/` - Material You colors, typography, AOD support
   - `components/` - Reusable widgets (TideGraph, DirectionIndicator, etc.)
   - `app/` - Main screens (to be implemented)
   - `tile/` - WearOS tile widget (to be implemented)

4. **Data Pipeline** (`tools/data-pipeline/`)
   - `fetch_noaa_data.py` - Fetch stations from NOAA CO-OPS API
   - `build_database.py` - Generate SQLite database
   - Output: `tides.db` bundled in app assets

### Key Technical Decisions

- **Calculation Method**: NOAA-style harmonic analysis with 37 constituents
- **Caching Strategy**: Pre-compute 7-day extrema, calculate current height on-demand
- **Database**: Room with bundled SQLite, copied to app storage on first launch
- **Timezone**: Calculate in UTC, display in watch timezone
- **Datum**: MLLW (Mean Lower Low Water) for all heights
- **Battery Target**: <2% per hour with screen on

## Development Commands

### Data Pipeline

```bash
# Generate tide database (run before first build)
cd tools/data-pipeline

# Recommended: Use uv (modern Python package manager)
./run.sh                        # Runs both scripts automatically
# Or manually:
uv run fetch_noaa_data.py      # Fetch from NOAA API
uv run build_database.py        # Build SQLite database
cp tides.db ../../app/src/main/assets/

# Alternative: Use pip (requires manual dependency installation)
pip install -r requirements.txt
python fetch_noaa_data.py
python build_database.py
cp tides.db ../../app/src/main/assets/
```

### Build & Test

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Lint
./gradlew lint

# Install on device/emulator
./gradlew installDebug
```

### Testing

- **Emulator**: Use WearOS emulator from Android Studio Device Manager
- **Validation**: Compare calculated tides against NOAA predictions
- **Test Stations**: 9414290 (SF), 8454000 (Providence), 8518750 (NYC)
- **Accuracy Target**: ±0.1 ft for height, ±2 minutes for times

## Implementation Status

**Completed (12/20 tasks)**:
- ✅ Project structure and dependencies
- ✅ Data models and database schema
- ✅ Full calculation engine (Constituents, Astronomical, Harmonic, Cache)
- ✅ Python data pipeline
- ✅ UI theme and reusable components
- ✅ Documentation (README, LICENSE, CONTRIBUTING)

**Remaining (8/20 tasks)**:
- ❌ Main UI screens (MainActivity, TideMainScreen, StationPicker, Detail, Settings)
- ❌ Tile widget
- ❌ AOD optimization
- ❌ Unit tests
- ❌ GitHub Actions workflows

See `docs/IMPLEMENTATION_STATUS.md` for detailed status and `docs/NEXT_STEPS.md` for completion guide.

## Code Style

- Follow Kotlin coding conventions
- Use meaningful names (e.g., `calculateHeight` not `calcH`)
- Add KDoc comments for public APIs
- Keep functions focused (single responsibility)
- Prefer immutability (val over var, data classes)

## Important Notes

- The harmonic calculation engine is production-ready and thoroughly documented
- Database must be generated from data pipeline before first build
- All tide heights are relative to MLLW datum
- Times are stored in UTC, converted to local for display
- Newton's method is used for finding extrema (high/low tides)

## Quick Start for New Contributors

1. Install uv: `curl -LsSf https://astral.sh/uv/install.sh | sh`
2. Generate database: `cd tools/data-pipeline && ./run.sh`
3. Copy database: `cp tools/data-pipeline/tides.db app/src/main/assets/`
4. Open project in Android Studio
5. Create WearOS emulator: Device Manager > Create Device > Wear OS
6. Run app: `./gradlew installDebug`
7. See `CONTRIBUTING.md` for contribution guidelines

## References

- NOAA API: https://api.tidesandcurrents.noaa.gov/
- WearOS Compose: https://developer.android.com/training/wearables/compose
- Spec: `docs/plans/SPEC.md`
