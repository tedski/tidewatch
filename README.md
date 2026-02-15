# TideWatch

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![WearOS](https://img.shields.io/badge/WearOS-3%2B-green.svg)](https://wearos.google.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org/)
[![API](https://img.shields.io/badge/API-30%2B-brightgreen.svg)](https://developer.android.com/about/versions/11)

> Offline-first tide prediction app for WearOS smartwatches using harmonic analysis of NOAA data

<!-- Screenshot placeholder - add once UI is finalized -->

## Features

- **Fully Offline**: Complete functionality without network connection after installation
- **Accurate Predictions**: NOAA-grade harmonic analysis with 37 tidal constituents
- **Battery Efficient**: <2% battery per hour with optimized caching strategy
- **Privacy-First**: No tracking, analytics, or accounts required
- **Location-Aware**: Automatic nearby station search (optional permission)
- **Native WearOS**: Tile widget and Always-On Display support

## Installation

### From Source

1. Clone the repository and generate the tide database:
   ```bash
   git clone https://github.com/yourusername/tidewatch.git
   cd tidewatch/tools/data-pipeline

   # Install uv (recommended) or use pip
   curl -LsSf https://astral.sh/uv/install.sh | sh

   # Generate database (test mode: 5 stations, ~30 seconds)
   ./run.sh
   cp tides-test.db ../../app/src/main/assets/tides.db
   ```

2. Open in Android Studio and build:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install on your WearOS device or emulator

See [DEVELOPMENT.md](docs/DEVELOPMENT.md) for detailed build instructions.

### From Releases

Pre-built APKs will be available in [Releases](https://github.com/yourusername/tidewatch/releases) once the app reaches stable version.

## Quick Start

1. **First Launch**: Grant location permission (optional) to find nearby stations
2. **Select Station**: Browse by state or use "Nearby" for closest stations
3. **View Tides**: See current tide, next high/low, and 24-hour graph
4. **Add Tile**: Long-press watch face → Add Tile → TideWatch for glanceable info

## Development

### Prerequisites
- Android Studio Giraffe+
- JDK 17+
- WearOS emulator or physical device

### Build System
```bash
./gradlew assembleDebug     # Build debug APK
./gradlew test              # Run unit tests
./gradlew installDebug      # Install on device
```

### Data Pipeline
Generate the tide database from NOAA data:
```bash
cd tools/data-pipeline
./run.sh --mode test        # Test: 5 stations, ~30 sec
./run.sh --mode production  # Production: 3,379 stations, ~45 min
```

See [DEVELOPMENT.md](docs/DEVELOPMENT.md) for comprehensive developer guide.

## Contributing

Contributions are welcome! We're particularly interested in:

- UI/UX improvements for small circular screens
- Battery optimization
- Test coverage
- Internationalization
- Documentation

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a pull request.

## Documentation

- **[Architecture](docs/ARCHITECTURE.md)** - System architecture and calculation engine
- **[Development](docs/DEVELOPMENT.md)** - Comprehensive developer guide
- **[Design](docs/DESIGN.md)** - Full design specification and reference
- **[Roadmap](docs/ROADMAP.md)** - Feature roadmap and development status

See [docs/README.md](docs/README.md) for complete documentation index.

## Data Sources

Tide predictions are based on:
- **NOAA CO-OPS**: ~3,000 harmonic tide stations (US waters)
- **Public Domain**: NOAA data is US Government work
- **API**: https://api.tidesandcurrents.noaa.gov/

TideWatch is not affiliated with or endorsed by NOAA. For critical navigation or safety decisions, always consult official NOAA sources.

## Technical Highlights

- **Calculation Method**: Standard harmonic analysis (h = Σ[A×f×cos(ω×t+φ-κ)])
- **37 Constituents**: Complete NOAA tidal constituent set
- **Hybrid Caching**: Pre-compute 7-day extrema, calculate current height on-demand
- **Room Database**: ~3,000 stations with harmonic constants bundled in app
- **WearOS Native**: Jetpack Compose for Wear OS

See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for detailed technical information.

## License

This project is licensed under the GNU General Public License v3.0 - see [LICENSE](LICENSE) for details.

NOAA data is public domain. The NOAA logo and branding are not used in this app.

## Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/tidewatch/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/tidewatch/discussions)
- **Security**: See [SECURITY.md](SECURITY.md) for vulnerability reporting

## Acknowledgments

- **NOAA Center for Operational Oceanographic Products and Services** for comprehensive tide data and APIs
- **WearOS development community** for tools and guidance
- **XTide** for harmonic calculation reference implementation

## References

- [NOAA CO-OPS API](https://api.tidesandcurrents.noaa.gov/api/prod/)
- [Harmonic Analysis of Tides (Schureman, 1958)](https://tidesandcurrents.noaa.gov/publications/)
- [WearOS Design Guidelines](https://developer.android.com/training/wearables/design)
- [Jetpack Compose for WearOS](https://developer.android.com/training/wearables/compose)

---

**TideWatch** - Accurate tide predictions for coastal adventures
