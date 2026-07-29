"""Configuration for the import pipeline."""

import os
from pathlib import Path


# Base directory for the pipeline
BASE_DIR = Path(__file__).resolve().parent.parent

# Data directories
DATA_DIR = BASE_DIR / "data"
SSA_DATA_DIR = Path(os.environ.get("SSA_DATA_DIR", DATA_DIR / "ssa" / "raw"))
OUTPUT_DIR = DATA_DIR / "output"

# Ensure directories exist
SSA_DATA_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# SSA bulk archive URL - contains all names from 1880 to present
# Format: names.zip containing yob{year}.txt files
SSA_BULK_ARCHIVE_URL = "https://www.ssa.gov/oact/babynames/names.zip"

# USA country code
USA_COUNTRY_CODE = "US"

# Canonical CSV output path
CANONICAL_CSV_PATH = OUTPUT_DIR / "names_canonical.csv"


def get_database_url() -> str:
    """Get the database URL from environment."""
    url = os.environ.get("DATABASE_URL")
    if not url:
        raise ValueError("DATABASE_URL environment variable is required")
    return url


def get_ssa_file_path(year: int) -> Path:
    """Get the path for a specific SSA data file."""
    return SSA_DATA_DIR / f"yob{year}.txt"
