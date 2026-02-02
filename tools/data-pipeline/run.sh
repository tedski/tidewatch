#!/bin/bash
# Run data pipeline using uv

set -e

echo "Running TideWatch data pipeline with uv..."
echo ""
echo "Step 1: Fetching NOAA station data..."
uv run fetch_noaa_data.py
echo ""
echo "Step 2: Building SQLite database..."
uv run build_database.py
echo ""
echo "Done! Database created at tides.db"
echo "Copy to app assets: cp tides.db ../../app/src/main/assets/"
