# TideWatch Data Pipeline

Scripts to fetch tide station data from NOAA and build the SQLite database.

## Overview

The data pipeline consists of two scripts:

1. `fetch_noaa_data.py` - Queries NOAA CO-OPS API for station data
2. `build_database.py` - Converts JSON data to SQLite database

## Quick Start (Recommended)

**Using uv** (modern Python package manager):

```bash
# Install uv once (if not already installed)
curl -LsSf https://astral.sh/uv/install.sh | sh

# Run the full pipeline
./run.sh

# Or run steps individually
uv run fetch_noaa_data.py
uv run build_database.py
cp tides.db ../../app/src/main/assets/
```

uv automatically manages virtual environments and dependencies - no manual setup needed!

## Alternative Methods

### Using pip (traditional)

```bash
pip install -r requirements.txt
python fetch_noaa_data.py
python build_database.py
cp tides.db ../../app/src/main/assets/
```

### Using Python venv (manual isolation)

```bash
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate
pip install -r requirements.txt
python fetch_noaa_data.py
python build_database.py
cp tides.db ../../app/src/main/assets/
```

## What Each Step Does

### 1. Fetch NOAA data

```bash
uv run fetch_noaa_data.py
```

This creates `stations.json` with station metadata and harmonic constituents.

For the MVP, this script fetches a sample of 5 well-known stations. In production,
you would modify it to fetch all ~3,000 NOAA stations.

### 2. Build database

```bash
uv run build_database.py
```

This creates `tides.db` from the JSON data.

### 3. Copy to app assets

```bash
cp tides.db ../../app/src/main/assets/
```

The app will copy this database to its data directory on first launch.

## NOAA API Reference

- **Stations API**: https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json
- **Harmonic Constituents**: https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations/{id}/harcon.json
- **Documentation**: https://api.tidesandcurrents.noaa.gov/api/prod/

## Database Schema

### stations
- `id` (TEXT, PK) - NOAA station ID
- `name` (TEXT) - Station name
- `state` (TEXT) - State/region
- `latitude` (REAL) - Decimal degrees
- `longitude` (REAL) - Decimal degrees
- `type` (TEXT) - "harmonic" or "subordinate"
- `timezoneOffset` (INTEGER) - UTC offset in minutes
- `referenceStationId` (TEXT, nullable) - For subordinate stations

### harmonic_constituents
- `stationId` (TEXT, FK)
- `constituentName` (TEXT) - e.g., "M2", "S2"
- `amplitude` (REAL) - in feet or meters
- `phaseLocal` (REAL) - in degrees

### subordinate_offsets
- `stationId` (TEXT, PK, FK)
- `referenceStationId` (TEXT, FK)
- `timeOffsetHigh` (INTEGER) - minutes
- `timeOffsetLow` (INTEGER) - minutes
- `heightOffsetHigh` (REAL) - multiplier
- `heightOffsetLow` (REAL) - multiplier

## Future Enhancements

- Automated monthly updates via GitHub Actions
- Full dataset (all 3,000+ stations)
- Subordinate station offset data
- Database versioning and migration
- Incremental updates (only changed stations)
