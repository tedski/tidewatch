# TideWatch

> Offline-first tide prediction app for WearOS smartwatches

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![WearOS](https://img.shields.io/badge/WearOS-3%2B-green.svg)](https://wearos.google.com/)

TideWatch is an open-source WearOS app that provides accurate tide predictions for fishing and coastal activities, working completely offline using harmonic analysis of NOAA station data.

## Features

- **Offline-first**: Complete functionality without network after installation
- **Accurate predictions**: NOAA-style harmonic analysis with 37 tidal constituents
- **Battery efficient**: Hybrid caching strategy pre-computes 7-day extrema
- **Privacy-focused**: No tracking, analytics, or accounts required
- **Location-aware**: Find nearest stations automatically (optional permission)
- **Tile widget**: Glanceable tide info from your watch face
- **Always-On Display**: Optimized for AOD mode with minimal battery impact

## Implementation Status

### ✅ Completed

**Foundation (Day 1-2)**
- [x] WearOS project structure with Gradle + Kotlin DSL
- [x] Data models (Station, HarmonicConstituent, TideExtremum, etc.)
- [x] Room database schema with DAOs
- [x] StationRepository with location-based search
- [x] Python data pipeline scripts (NOAA API integration)

**Calculation Engine (Day 2)**
- [x] 37 NOAA tidal constituent definitions
- [x] AstronomicalCalculator (node factors, equilibrium arguments)
- [x] HarmonicCalculator (core tide prediction engine)
- [x] TideCache (7-day extrema pre-computation)
- [x] Newton's method for finding high/low tides

**UI Components (Day 3)**
- [x] Material You theme (optimized for WearOS)
- [x] TideDirectionIndicator (rising/falling/slack with rate)
- [x] ExtremumCard (high/low tide display)
- [x] TideGraph (24-hour tide curve visualization)
- [x] StationList (scrollable station picker)

### 🚧 In Progress / Remaining

**UI Screens**
- [ ] Main tide display screen
- [ ] Station picker screen
- [ ] Detail screen (full tide curve + 7-day list)
- [ ] Settings screen
- [ ] Navigation setup

**Features**
- [ ] Tile widget implementation
- [ ] AOD optimization
- [ ] Location permission handling
- [ ] App initialization (database copy from assets)

**Testing & Validation**
- [ ] Unit tests for harmonic calculations
- [ ] NOAA prediction comparison tests
- [ ] Integration tests

**Infrastructure**
- [ ] GitHub Actions CI/CD
- [ ] Data update automation
- [ ] Release workflow

## Architecture

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
│  │  HarmonicCalculator               │  │
│  │  - 37 tidal constituents          │  │
│  │  - Node factors (astronomical)    │  │
│  │  - Newton's method for extrema    │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │  TideCache (7-day extrema)        │  │
│  └───────────────────────────────────┘  │
└────────────┬────────────────────────────┘
             │
┌────────────┴────────────────────────────┐
│        Station Database (Room)          │
│  - ~3,000 NOAA stations                 │
│  - Harmonic constituents per station    │
│  - Station metadata (location, etc.)    │
└─────────────────────────────────────────┘
```

## Technical Details

### Harmonic Analysis

TideWatch uses the standard harmonic method employed by NOAA:

```
h(t) = Σ[A_i × f_i × cos(ω_i × t + φ_i - κ_i)]
```

Where:
- `A_i` = constituent amplitude (from database)
- `f_i` = node factor (from astronomical calculation)
- `ω_i` = angular velocity (constituent speed)
- `φ_i` = local phase (from database)
- `κ_i` = equilibrium argument (from astronomical position)

### Battery Optimization

The hybrid caching strategy minimizes battery impact:

1. **On station selection**: Pre-compute 7 days of high/low tides (~200ms)
2. **On UI update**: Calculate current height only (~5ms)
3. **Tile updates**: Every 5 minutes or on screen wake
4. **AOD mode**: Reduce to 15-minute updates

Target: <2% battery per hour with screen on

## Build Instructions

### Prerequisites

- Android Studio Giraffe or later
- Android SDK 34
- JDK 17
- WearOS emulator or physical device

### Setup

1. Clone the repository:
```bash
git clone https://github.com/yourusername/tidewatch.git
cd tidewatch
```

2. Generate the tide database:
```bash
cd tools/data-pipeline
pip install -r requirements.txt
python fetch_noaa_data.py
python build_database.py
cp tides.db ../../app/src/main/assets/
```

3. Open in Android Studio and sync Gradle

4. Run on WearOS emulator or device

## Data Pipeline

The `tools/data-pipeline/` directory contains scripts to fetch NOAA data:

- `fetch_noaa_data.py` - Queries NOAA CO-OPS API for stations
- `build_database.py` - Builds SQLite database from JSON
- Output: `tides.db` bundled in app assets

See [data pipeline README](tools/data-pipeline/README.md) for details.

## Testing

### Unit Tests

```bash
./gradlew test
```

### Validation Against NOAA

Unit tests compare calculated predictions against published NOAA data:
- Acceptable error: ±0.1 ft for height, ±2 minutes for times
- Test multiple stations and tidal regimes

## Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

Key areas for contribution:
- UI/UX improvements for small screens
- Additional stations (international)
- Tidal current predictions
- Watch face complications
- Multi-language support

## Data Sources

- **Tide Data**: NOAA CO-OPS (public domain)
- **API**: https://api.tidesandcurrents.noaa.gov/
- **Constituents**: NOAA Special Publication NOS CO-OPS 3

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

**Note**: NOAA data is public domain (US Government work). The NOAA logo and branding are not used in this app.

## References

- [NOAA CO-OPS API Documentation](https://api.tidesandcurrents.noaa.gov/api/prod/)
- [Harmonic Analysis of Tides (Schureman, 1958)](https://tidesandcurrents.noaa.gov/publications/)
- [WearOS Design Guidelines](https://developer.android.com/training/wearables/design)
- [Jetpack Compose for WearOS](https://developer.android.com/training/wearables/compose)

## Roadmap

### MVP (Current Phase)
- Core tide prediction functionality
- Basic UI with main screen and station picker
- Tile widget
- Location-based station search

### Post-MVP
- Watch face complications
- Tidal current stations (different calculation)
- Notification for optimal tide windows
- Moon phase display
- Export tide data for trip planning
- Offline map view for station selection

## Support

For bug reports and feature requests, please use the [GitHub Issues](https://github.com/yourusername/tidewatch/issues) page.

## Acknowledgments

- NOAA for providing comprehensive tide data and APIs
- The open-source community for WearOS development tools
- Maritime community for feedback and testing

---

**Made with ❤️ for fishers, surfers, and coastal enthusiasts**
