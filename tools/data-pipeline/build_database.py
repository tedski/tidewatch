#!/usr/bin/env python3
"""
Build SQLite database from fetched NOAA station data.

Reads stations.json and creates a SQLite database with:
- stations table
- harmonic_constituents table
- subordinate_offsets table

Output: tides.db (SQLite database)
"""

import json
import sqlite3
import sys
from pathlib import Path
from typing import Any, Dict, List


def create_schema(conn: sqlite3.Connection):
    """
    Create database schema.

    Args:
        conn: SQLite connection
    """
    cursor = conn.cursor()

    # Stations table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS stations (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            state TEXT NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            type TEXT NOT NULL,
            timezoneOffset INTEGER NOT NULL,
            referenceStationId TEXT
        )
    """)

    # Harmonic constituents table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS harmonic_constituents (
            stationId TEXT NOT NULL,
            constituentName TEXT NOT NULL,
            amplitude REAL NOT NULL,
            phaseLocal REAL NOT NULL,
            PRIMARY KEY (stationId, constituentName),
            FOREIGN KEY (stationId) REFERENCES stations(id) ON DELETE CASCADE
        )
    """)

    # Create index for faster queries
    cursor.execute("""
        CREATE INDEX IF NOT EXISTS idx_constituents_station
        ON harmonic_constituents(stationId)
    """)

    # Subordinate offsets table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS subordinate_offsets (
            stationId TEXT PRIMARY KEY,
            referenceStationId TEXT NOT NULL,
            timeOffsetHigh INTEGER NOT NULL,
            timeOffsetLow INTEGER NOT NULL,
            heightOffsetHigh REAL NOT NULL,
            heightOffsetLow REAL NOT NULL,
            FOREIGN KEY (stationId) REFERENCES stations(id) ON DELETE CASCADE,
            FOREIGN KEY (referenceStationId) REFERENCES stations(id) ON DELETE CASCADE
        )
    """)

    conn.commit()
    print("✓ Database schema created")


def insert_station(conn: sqlite3.Connection, station_data: Dict[str, Any]):
    """
    Insert a station into the database.

    Args:
        conn: SQLite connection
        station_data: Station data dictionary
    """
    cursor = conn.cursor()

    station_id = station_data.get("id")
    name = station_data.get("name", "Unknown")
    state = station_data.get("state", station_data.get("region", "Unknown"))
    lat = float(station_data.get("lat", 0.0))
    lon = float(station_data.get("lng", 0.0))
    station_type = station_data.get("type", "harmonic")

    # Get timezone offset (default to 0 if not available)
    timezone_offset = station_data.get("timezoneOffset", 0)

    # Reference station (for subordinate stations)
    reference_station_id = station_data.get("referenceStationId")

    cursor.execute("""
        INSERT OR REPLACE INTO stations
        (id, name, state, latitude, longitude, type, timezoneOffset, referenceStationId)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, (station_id, name, state, lat, lon, station_type, timezone_offset, reference_station_id))


def insert_harmonics(conn: sqlite3.Connection, station_id: str, harmonics: Dict[str, Any]):
    """
    Insert harmonic constituents for a station.

    Args:
        conn: SQLite connection
        station_id: Station ID
        harmonics: Harmonic data dictionary
    """
    cursor = conn.cursor()

    constituents = harmonics.get("HarmonicConstituents", [])

    for constituent in constituents:
        name = constituent.get("name")
        amplitude = float(constituent.get("amplitude", 0.0))
        phase = float(constituent.get("phase_GMT", 0.0))

        cursor.execute("""
            INSERT OR REPLACE INTO harmonic_constituents
            (stationId, constituentName, amplitude, phaseLocal)
            VALUES (?, ?, ?, ?)
        """, (station_id, name, amplitude, phase))


def build_database(stations_file: str, output_db: str):
    """
    Build SQLite database from stations JSON file.

    Args:
        stations_file: Path to stations.json
        output_db: Path to output database file
    """
    # Load stations data
    print(f"Reading {stations_file}...")
    with open(stations_file, "r") as f:
        stations = json.load(f)

    print(f"Loaded {len(stations)} stations")

    # Create database
    print(f"\nCreating database: {output_db}")

    # Remove existing database
    db_path = Path(output_db)
    if db_path.exists():
        db_path.unlink()
        print("  Removed existing database")

    # Connect to database
    conn = sqlite3.connect(output_db)

    try:
        # Create schema
        create_schema(conn)

        # Insert stations
        print("\nInserting stations...")
        harmonic_count = 0
        subordinate_count = 0

        for station_data in stations:
            station_id = station_data.get("id")
            station_type = station_data.get("type", "harmonic")

            # Insert station
            insert_station(conn, station_data)

            # Insert harmonics if available
            if station_type == "harmonic" and "harmonics" in station_data:
                insert_harmonics(conn, station_id, station_data["harmonics"])
                harmonic_count += 1
            else:
                subordinate_count += 1

        conn.commit()

        # Print summary
        print()
        print("=" * 60)
        print("Database build complete!")
        print("=" * 60)
        print(f"  Database file: {output_db}")
        print(f"  Total stations: {len(stations)}")
        print(f"  Harmonic stations: {harmonic_count}")
        print(f"  Subordinate stations: {subordinate_count}")

        # Get database size
        db_size_kb = db_path.stat().st_size / 1024
        print(f"  Database size: {db_size_kb:.1f} KB")
        print()

        # Verify database
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM stations")
        station_count = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM harmonic_constituents")
        constituent_count = cursor.fetchone()[0]

        print("Verification:")
        print(f"  Stations in DB: {station_count}")
        print(f"  Constituents in DB: {constituent_count}")
        print()

        print(f"Next step: Copy {output_db} to app/src/main/assets/")

    finally:
        conn.close()


def main():
    """Main execution function."""
    print("=" * 60)
    print("NOAA Tide Database Builder")
    print("=" * 60)
    print()

    stations_file = "stations.json"
    output_db = "tides.db"

    # Check if input file exists
    if not Path(stations_file).exists():
        print(f"Error: {stations_file} not found", file=sys.stderr)
        print("Run fetch_noaa_data.py first", file=sys.stderr)
        sys.exit(1)

    build_database(stations_file, output_db)


if __name__ == "__main__":
    main()
