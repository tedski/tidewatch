#!/usr/bin/env python3
"""
Fetch tide station data from NOAA CO-OPS API.

This script queries the NOAA Tides & Currents API to retrieve:
- All harmonic tide prediction stations
- Harmonic constituent data for each station
- Station metadata (location, name, timezone, etc.)

Output: stations.json with all collected data
"""

import json
import sys
import time
from typing import Any, Dict, List, Optional
from urllib.parse import urlencode

import requests

# NOAA API endpoints
STATIONS_API = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json"
HARCON_API = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations/{station_id}/harcon.json"

# API request settings
REQUEST_TIMEOUT = 30  # seconds
REQUEST_DELAY = 0.5  # seconds between requests to be respectful


def fetch_stations() -> List[Dict[str, Any]]:
    """
    Fetch all tide prediction stations from NOAA.

    Returns:
        List of station dictionaries
    """
    print("Fetching tide stations from NOAA API...")

    params = {
        "type": "tidepredictions",
        "expand": "details"
    }

    url = f"{STATIONS_API}?{urlencode(params)}"

    try:
        response = requests.get(url, timeout=REQUEST_TIMEOUT)
        response.raise_for_status()
        data = response.json()

        stations = data.get("stations", [])
        print(f"Found {len(stations)} tide prediction stations")

        return stations

    except requests.exceptions.RequestException as e:
        print(f"Error fetching stations: {e}", file=sys.stderr)
        sys.exit(1)


def fetch_harmonic_constituents(station_id: str) -> Optional[Dict[str, Any]]:
    """
    Fetch harmonic constituent data for a specific station.

    Args:
        station_id: NOAA station ID

    Returns:
        Dictionary with harmonic constituents, or None if not available
    """
    url = HARCON_API.format(station_id=station_id)

    try:
        response = requests.get(url, timeout=REQUEST_TIMEOUT)

        # Some stations may not have harmonic data (e.g., subordinate stations)
        if response.status_code == 404:
            return None

        response.raise_for_status()
        data = response.json()

        return data

    except requests.exceptions.RequestException as e:
        print(f"Warning: Could not fetch harmonics for {station_id}: {e}", file=sys.stderr)
        return None


def main():
    """Main execution function."""
    print("=" * 60)
    print("NOAA Tide Data Fetcher")
    print("=" * 60)
    print()

    # Fetch all stations
    stations = fetch_stations()

    # For MVP, we'll create a sample dataset with just a few well-known stations
    # In production, you would fetch all stations
    sample_stations = [
        "9414290",  # San Francisco, CA
        "8454000",  # Providence, RI
        "8518750",  # The Battery, NY
        "8658120",  # Wilmington, NC
        "8636580",  # Cape Hatteras, NC
    ]

    print(f"\nFetching harmonic constituents for {len(sample_stations)} sample stations...")
    print("(In production, this would fetch all ~3000 stations)")
    print()

    enriched_stations = []

    for i, station_data in enumerate(stations):
        station_id = station_data.get("id")

        # For MVP, only process sample stations
        if station_id not in sample_stations:
            continue

        print(f"[{i+1}/{len(sample_stations)}] Processing station {station_id}: {station_data.get('name')}...")

        # Fetch harmonic constituents
        time.sleep(REQUEST_DELAY)  # Be respectful to API
        harmonics = fetch_harmonic_constituents(station_id)

        if harmonics:
            station_data["harmonics"] = harmonics
            station_data["type"] = "harmonic"
            print(f"  ✓ Found {len(harmonics.get('HarmonicConstituents', []))} constituents")
        else:
            station_data["type"] = "subordinate"
            print(f"  - No harmonic data (subordinate station)")

        enriched_stations.append(station_data)

    # Save to JSON file
    output_file = "stations.json"
    with open(output_file, "w") as f:
        json.dump(enriched_stations, f, indent=2)

    print()
    print(f"✓ Data saved to {output_file}")
    print(f"  Total stations: {len(enriched_stations)}")
    print(f"  Harmonic stations: {sum(1 for s in enriched_stations if s.get('type') == 'harmonic')}")
    print(f"  Subordinate stations: {sum(1 for s in enriched_stations if s.get('type') == 'subordinate')}")
    print()
    print("Next step: Run build_database.py to create SQLite database")


if __name__ == "__main__":
    main()
