"""Configuration for the import pipeline."""

import os
from pathlib import Path


# Base directory for the pipeline
BASE_DIR = Path(__file__).resolve().parent.parent

# Data directories
DATA_DIR = BASE_DIR / "data"
SSA_DATA_DIR = Path(os.environ.get("SSA_DATA_DIR", DATA_DIR / "ssa" / "raw"))
SCB_DATA_DIR = Path(os.environ.get("SCB_DATA_DIR", DATA_DIR / "scb" / "raw"))
OUTPUT_DIR = DATA_DIR / "output"

# Ensure directories exist
SSA_DATA_DIR.mkdir(parents=True, exist_ok=True)
SCB_DATA_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# USA country code
USA_COUNTRY_CODE = "US"

# Sweden country code
SWEDEN_COUNTRY_CODE = "SE"

# Canonical CSV output path
CANONICAL_CSV_PATH = OUTPUT_DIR / "names_canonical.csv"

# SCB API base URL
SCB_API_BASE = "https://api.scb.se/OV0104/v1/doris"

# SCB database and table paths
SCB_DBID = "ssd"
SCB_BE_CATEGORY = "BE"
SCB_NAME_STATISTICS = "BE0001"

# SCB table IDs (under BE0001 - Name statistics)
SCB_TABLES = {
    "BE0001D": "Newborn - Old tables not updated",
    "BE0001G": "All registered persons in Sweden - Old tables not updated",
}

# Swedish language code for SCB API
SCB_SWEDISH_LANG = "sv"


def get_database_url() -> str:
    """Get the database URL from environment."""
    url = os.environ.get("DATABASE_URL")
    if not url:
        raise ValueError("DATABASE_URL environment variable is required")
    return url


def get_ssa_file_path(year: int) -> Path:
    """Get the path for a specific SSA data file."""
    return SSA_DATA_DIR / f"yob{year}.txt"


def get_scb_file_path(filename: str) -> Path:
    """Get the path for a specific SCB data file."""
    return SCB_DATA_DIR / filename
