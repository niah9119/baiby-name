"""Fetch SSA baby names data from the web."""

import os
import re
import time
from pathlib import Path
from typing import Optional

import requests
from requests.exceptions import RequestException

from .config import SSA_BASE_URL, SSA_DATA_DIR, get_ssa_file_path


def fetch_ssa_year(year: int, output_dir: Optional[Path] = None) -> Path:
    """
    Fetch SSA baby names data for a specific year.

    Args:
        year: The year to fetch data for (1880+)
        output_dir: Optional directory to save the file, defaults to config.SSA_DATA_DIR

    Returns:
        Path to the downloaded file

    Raises:
        RequestException: If the request fails
    """
    output_path = output_dir or SSA_DATA_DIR
    output_path.mkdir(parents=True, exist_ok=True)
    file_path = output_path / f"yob{year}.txt"

    # Check if file already exists
    if file_path.exists():
        print(f"File already exists: {file_path}")
        return file_path

    # SSA provides data via direct download with year parameter
    # We'll try multiple URL patterns
    urls = [
        f"https://www.ssa.gov/cgi-bin/babyname.cgi?year={year}&top=1000&num=1000",
        f"https://www.ssa.gov/cgi-bin/babyname.cgi?year={year}",
    ]

    headers = {
        "User-Agent": "Mozilla/5.0 (BaibyName Pipeline; +https://github.com/baiby-name)",
    }

    for url in urls:
        try:
            response = requests.get(url, headers=headers, timeout=60)
            response.raise_for_status()

            # Check if response looks like valid data
            content = response.text
            if _is_valid_ssa_content(content, year):
                with open(file_path, "w", encoding="utf-8") as f:
                    f.write(content)
                print(f"Downloaded: {file_path}")
                return file_path

            # Rate limiting - be polite to SSA servers
            time.sleep(1)

        except RequestException as e:
            print(f"Failed to fetch {year}: {e}")
            continue

    raise RequestException(f"Could not fetch data for year {year}")


def _is_valid_ssa_content(content: str, year: int) -> bool:
    """Check if the content looks like valid SSA data."""
    # SSA format: name,sex,count per line
    lines = content.strip().split("\n")[:5]
    for line in lines:
        line = line.strip()
        if line:
            parts = line.split(",")
            if len(parts) == 3:
                name, sex, count = parts
                if len(name) > 0 and sex in ("M", "F", "A") and count.isdigit():
                    return True
    return False


def fetch_all_years(start_year: int = 1880, end_year: int = None) -> list[Path]:
    """
    Fetch SSA data for all years in the range.

    Args:
        start_year: First year to fetch (default: 1880)
        end_year: Last year to fetch (default: current year - 1)

    Returns:
        List of paths to downloaded files
    """
    if end_year is None:
        end_year = int(time.strftime("%Y")) - 1

    paths = []
    for year in range(start_year, end_year + 1):
        try:
            path = fetch_ssa_year(year)
            paths.append(path)
            # Be respectful to the server
            time.sleep(0.5)
        except Exception as e:
            print(f"Error fetching year {year}: {e}")

    return paths


def fetch_usa_data() -> list[Path]:
    """
    Fetch all USA SSA data (backward compatibility wrapper).

    Returns:
        List of paths to downloaded files
    """
    return fetch_all_years()


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Fetch SSA baby names data")
    parser.add_argument("--year", type=int, help="Specific year to fetch")
    parser.add_argument("--start-year", type=int, default=1880, help="Start year for batch fetch")
    parser.add_argument("--end-year", type=int, help="End year for batch fetch")
    parser.add_argument("--output-dir", type=str, help="Output directory for downloaded files")

    args = parser.parse_args()

    output_dir = Path(args.output_dir) if args.output_dir else None

    if args.year:
        path = fetch_ssa_year(args.year, output_dir)
        print(f"Fetched: {path}")
    else:
        paths = fetch_all_years(args.start_year, args.end_year)
        print(f"Fetched {len(paths)} files")
