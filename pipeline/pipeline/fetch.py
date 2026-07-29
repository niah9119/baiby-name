"""Fetch SSA baby names data from the web."""

import io
import re
import time
import zipfile
from pathlib import Path
from typing import Optional

import requests
from requests.exceptions import RequestException

from .config import SSA_DATA_DIR


def _fetch_ssa_bulk_archive(output_dir: Optional[Path] = None) -> list[Path]:
    """
    Download and extract the complete SSA baby names archive.

    Downloads the bulk archive from SSA and extracts all year files.
    This is the most efficient way to get all names (not just top 1000).

    Args:
        output_dir: Optional directory to save files, defaults to config.SSA_DATA_DIR

    Returns:
        List of paths to extracted files

    Raises:
        RequestException: If the download fails (e.g., rate limiting or access denied)
    """
    output_path = output_dir or SSA_DATA_DIR
    output_path.mkdir(parents=True, exist_ok=True)

    # Bulk archive URL - contains all names from 1880 to present
    archive_url = "https://www.ssa.gov/oact/babynames/names.zip"

    headers = {
        "User-Agent": "BaibyName-Pipeline/1.0 (+https://github.com/baiby-name)",
    }

    print(f"Downloading SSA archive from {archive_url}...")
    try:
        response = requests.get(archive_url, headers=headers, timeout=120)
        response.raise_for_status()
    except requests.exceptions.HTTPError as e:
        if e.response is not None and e.response.status_code == 403:
            raise RequestException(
                f"Access denied when trying to download SSA data. "
                f"Please download the archive manually from {archive_url} "
                f"and extract it to {output_path}."
            ) from e
        raise

    # Read the zip file
    zip_content = io.BytesIO(response.content)
    zip_file = zipfile.ZipFile(zip_content)

    # Extract all year files
    extracted_files = []
    for name in zip_file.namelist():
        # Only extract yob{year}.txt files
        if re.match(r"^yob\d{4}\.txt$", name):
            year = _extract_year_from_filename(name)
            output_file = output_path / name
            if not output_file.exists():
                with open(output_file, "wb") as f:
                    f.write(zip_file.read(name))
                print(f"Extracted: {output_file}")
            extracted_files.append(output_file)

    zip_file.close()
    print(f"Downloaded and extracted {len(extracted_files)} files from SSA archive")
    return sorted(extracted_files)


def fetch_ssa_year(year: int, output_dir: Optional[Path] = None) -> Path:
    """
    Fetch SSA baby names data for a specific year.

    This function downloads the complete SSA archive (which is faster than
    fetching individual years) and returns the specific year file.

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

    # Download the bulk archive to get all years at once
    _fetch_ssa_bulk_archive(output_dir)

    # Verify the file was extracted
    if not file_path.exists():
        raise FileNotFoundError(f"Could not extract file for year {year}")

    return file_path


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
