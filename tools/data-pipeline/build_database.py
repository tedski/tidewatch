#!/usr/bin/env python3
"""
Build SQLite database from fetched NOAA station data.

Reads stations.json and creates a SQLite database with:
- stations table
- harmonic_constituents table
- subordinate_offsets table

Output: tides.db (SQLite database)
"""

import argparse
import json
import re
import sqlite3
import sys
from pathlib import Path
from typing import Any, Dict, List


def normalize_station_name(name: str) -> str:
    """
    Convert NOAA station names to title case for better readability.

    Handles special cases:
    - "SAN FRANCISCO" -> "San Francisco"
    - "NEW YORK (THE BATTERY)" -> "New York (The Battery)"
    - "O'BRIEN ISLAND" -> "O'Brien Island"
    - "MCDONALD POINT" -> "McDonald Point"

    Args:
        name: Raw station name from NOAA (typically ALL CAPS)

    Returns:
        Formatted station name in title case
    """
    if not name or name.isspace():
        return name

    # Minor words that should stay lowercase (except at start or after parentheses)
    minor_words = {'the', 'of', 'and', 'at', 'in', 'on', 'a', 'an', 'to'}

    def format_word(word: str, is_first: bool, after_paren: bool) -> str:
        """Format a single word with title case rules."""
        if not word:
            return word

        # Handle parentheses at start of word
        if word.startswith('('):
            if len(word) == 1:
                return word
            return '(' + format_word(word[1:], is_first=True, after_paren=True)

        # Handle apostrophes (O'Brien, McDonald's)
        if "'" in word:
            parts = word.split("'")
            return "'".join(part.capitalize() for part in parts)

        # Handle commas (preserve them)
        if word.endswith(','):
            return format_word(word[:-1], is_first, after_paren) + ','

        word_lower = word.lower()

        # Keep minor words lowercase unless first word or after parenthesis
        if not is_first and not after_paren and word_lower in minor_words:
            return word_lower

        # Capitalize first letter, lowercase the rest
        return word_lower.capitalize()

    # Split on spaces and process each word
    words = name.split()
    result = []

    for i, word in enumerate(words):
        is_first = (i == 0)
        after_paren = (i > 0 and result[-1].endswith('('))
        result.append(format_word(word, is_first, after_paren))

    return ' '.join(result)


def parse_arguments() -> argparse.Namespace:
    """
    Parse command-line arguments.

    Returns:
        Parsed arguments
    """
    parser = argparse.ArgumentParser(
        description="Build SQLite database from NOAA station data",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s                              # Test mode (tides-test.db)
  %(prog)s --mode production            # Production mode (tides.db)
  %(prog)s --input custom.json --output custom.db  # Custom files
        """
    )

    parser.add_argument(
        "--mode",
        choices=["test", "production"],
        default="test",
        help="Database mode: 'test' (tides-test.db) or 'production' (tides.db)"
    )

    parser.add_argument(
        "--input",
        type=str,
        default="stations.json",
        help="Input JSON file (default: stations.json)"
    )

    parser.add_argument(
        "--output",
        type=str,
        help="Output database file (overrides mode-based naming)"
    )

    return parser.parse_args()


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
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            state TEXT NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            type TEXT NOT NULL,
            timezoneOffset INTEGER NOT NULL,
            referenceStationId TEXT,
            datumOffset REAL NOT NULL DEFAULT 0.0
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

    # Subordinate offsets table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS subordinate_offsets (
            stationId TEXT NOT NULL PRIMARY KEY,
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

    Note:
        The database schema supports both harmonic and subordinate stations.
        As of 2026, NOAA provides harmonic data for virtually all active
        tide prediction stations, so subordinate stations are rare. The
        schema is maintained for future compatibility.
    """
    cursor = conn.cursor()

    station_id = station_data.get("id")
    name = normalize_station_name(station_data.get("name", "Unknown"))
    state = station_data.get("state", station_data.get("region", "Unknown"))
    lat = float(station_data.get("lat", 0.0))
    lon = float(station_data.get("lng", 0.0))
    station_type = station_data.get("type", "harmonic")

    # Get timezone offset (default to 0 if not available)
    timezone_offset = station_data.get("timezoneOffset", 0)

    # Reference station (for subordinate stations)
    reference_station_id = station_data.get("referenceStationId")

    # Datum offset Z₀ (MSL - MLLW in feet)
    datum_offset = station_data.get("datumOffset", 0.0)

    cursor.execute("""
        INSERT OR REPLACE INTO stations
        (id, name, state, latitude, longitude, type, timezoneOffset, referenceStationId, datumOffset)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (station_id, name, state, lat, lon, station_type, timezone_offset, reference_station_id, datum_offset))


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
        phase = float(constituent.get("phase_GMT", 0.0))  # Use phase_GMT for UTC calculations

        cursor.execute("""
            INSERT OR REPLACE INTO harmonic_constituents
            (stationId, constituentName, amplitude, phaseLocal)
            VALUES (?, ?, ?, ?)
        """, (station_id, name, amplitude, phase))


def insert_subordinate_offsets(conn: sqlite3.Connection, station_id: str, reference_id: str, offsets: Dict[str, Any]):
    """
    Insert subordinate station offsets.

    Args:
        conn: SQLite connection
        station_id: Subordinate station ID
        reference_id: Reference (harmonic) station ID
        offsets: Tide prediction offsets dictionary
    """
    cursor = conn.cursor()

    # Extract offset data from NOAA format
    # The offsets dict contains time and height corrections
    time_high = int(offsets.get("timeOffsetHighTide", 0))
    time_low = int(offsets.get("timeOffsetLowTide", 0))
    height_high = float(offsets.get("heightOffsetHighTide", 1.0))
    height_low = float(offsets.get("heightOffsetLowTide", 1.0))

    cursor.execute("""
        INSERT OR REPLACE INTO subordinate_offsets
        (stationId, referenceStationId, timeOffsetHigh, timeOffsetLow, heightOffsetHigh, heightOffsetLow)
        VALUES (?, ?, ?, ?, ?, ?)
    """, (station_id, reference_id, time_high, time_low, height_high, height_low))


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
                # Insert subordinate station offsets if available
                if "referenceStationId" in station_data and "tidepredoffsets" in station_data:
                    insert_subordinate_offsets(
                        conn,
                        station_id,
                        station_data["referenceStationId"],
                        station_data["tidepredoffsets"]
                    )
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
    args = parse_arguments()

    print("=" * 60)
    print("NOAA Tide Database Builder")
    print("=" * 60)
    print()

    # Determine output filename based on mode or explicit argument
    if args.output:
        output_db = args.output
    elif args.mode == "production":
        output_db = "tides.db"
    else:  # test mode
        output_db = "tides-test.db"

    # Check if input filename suggests custom mode
    if args.input != "stations.json" and not args.output:
        # Custom input without explicit output, use custom naming
        input_stem = Path(args.input).stem
        output_db = f"{input_stem}.db"

    print(f"Mode: {args.mode}")
    print(f"Input: {args.input}")
    print(f"Output: {output_db}")
    print()

    # Check if input file exists
    if not Path(args.input).exists():
        print(f"Error: {args.input} not found", file=sys.stderr)
        print("Run fetch_noaa_data.py first", file=sys.stderr)
        sys.exit(1)

    build_database(args.input, output_db)


if __name__ == "__main__":
    main()
