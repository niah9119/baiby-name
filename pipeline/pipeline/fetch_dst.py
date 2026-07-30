"""Fetch Denmark (DST) newborn-name statistics.

DST (Danmarks Statistik) publishes annual top-50 lists of newborn first names, split by
boys and girls, from 1993 onwards. For 1985-1992, the lists contain top 25 names.

## Source

The data is available via an AJAX endpoint on DST's website:

    https://www.dst.dk/DstDk-Global/sider/ajax.aspx?controlid=%7BE53ECEF3-D45A-4245-9544-1DE42E43A5D6%7D

The endpoint accepts a POST parameter `p1` with a year suffix:
- 1985-1999: suffix `_1` (e.g., `p1=1985_1`)
- 2000-2025: suffix `_2` (e.g., `p1=2024_2`)

Using the wrong suffix returns an empty page.

## Local-first pattern

This module follows the same pattern as `fetch_ssb.py`: it checks for a local copy first
and only fetches if missing or forced. The HTML files are stored as `dst-{year}.html`.

## Coverage

| Years | Depth per sex | Rows per file |
|-------|---------------|---------------|
| 1985-1992 | top 25 | 50 (25 girls + 25 boys) |
| 1993-2025 | top 50 | 100 (50 girls + 50 boys) |

Total: 3,700 rows across 41 years.
"""

from pathlib import Path
from typing import Optional

import requests
from requests.exceptions import RequestException

from .config import DST_DATA_DIR, DENMARK_COUNTRY_CODE

# The AJAX endpoint for DST name statistics
DST_AJAX_URL = (
    "https://www.dst.dk/DstDk-Global/sider/ajax.aspx"
    "?controlid=%7BE53ECEF3-D45A-4245-9544-1DE42E43A5D6%7D"
)

# User agent to mimic a browser (DST blocks some scripted clients)
USER_AGENT = "Mozilla/5.0 (compatible; baiby-name-agent/1.0)"

# Year suffix rule:
# - 1985-1999: suffix _1
# - 2000-2025: suffix _2
DST_MIN_YEAR = 1985
DST_MAX_YEAR = 2025


def _year_suffix(year: int) -> str:
    """Return the suffix for a given year.

    Args:
        year: The year (1985-2025)

    Returns:
        The suffix string ('_1' or '_2')

    Raises:
        ValueError: if year is outside the supported range
    """
    if not DST_MIN_YEAR <= year <= DST_MAX_YEAR:
        raise ValueError(f"Year {year} is outside supported range {DST_MIN_YEAR}-{DST_MAX_YEAR}")
    return "_1" if year < 2000 else "_2"


def _endpoint_payload(year: int) -> dict:
    """Build the POST payload for a given year."""
    suffix = _year_suffix(year)
    return {"p1": f"{year}{suffix}"}


def _local_filename(year: int) -> str:
    """Get the local filename for a given year."""
    return f"dst-{year}.html"


def local_dataset(year: Optional[int] = None, output_dir: Optional[Path] = None) -> Optional[Path]:
    """Return the already-downloaded dataset for a year, or None if not present.

    Args:
        year: The year to check. If None, check for any year.
        output_dir: Where to look for the file. Defaults to DST_DATA_DIR.

    Returns:
        Path to the local file if it exists and is non-empty, else None.
    """
    target_dir = output_dir or DST_DATA_DIR
    if year is not None:
        path = target_dir / _local_filename(year)
        if path.exists() and path.stat().st_size > 0:
            return path
        return None
    # Check for any year's file
    for y in range(DST_MIN_YEAR, DST_MAX_YEAR + 1):
        path = target_dir / _local_filename(y)
        if path.exists() and path.stat().st_size > 0:
            return path
    return None


def fetch_dst_year(year: int, output_dir: Optional[Path] = None, force: bool = False) -> Path:
    """Download DST name statistics for a specific year.

    Args:
        year: The year to fetch (1985-2025)
        output_dir: Where to write the HTML. Defaults to DST_DATA_DIR.
        force: Download even if a local copy exists.

    Returns:
        Path to the downloaded HTML file on disk.

    Raises:
        RequestException: the request failed.
        ValueError: year is outside the supported range.
    """
    target_dir = output_dir or DST_DATA_DIR
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / _local_filename(year)

    if not force:
        existing = local_dataset(year, target_dir)
        if existing is not None:
            print(f"Using local dataset: {existing}")
            return existing

    print(f"Fetching DST data for {year} from {DST_AJAX_URL}")

    payload = _endpoint_payload(year)

    try:
        response = requests.post(
            DST_AJAX_URL,
            data=payload,
            headers={"User-Agent": USER_AGENT},
            timeout=60
        )
        response.raise_for_status()
    except RequestException as exc:
        raise RequestException(f"Could not download DST data for {year}: {exc}") from exc

    # Verify we got actual data (the page should contain table rows)
    content = response.text
    if "<tr>" not in content or "<caption" not in content:
        raise RequestException(
            f"DST returned an empty or invalid response for {year}. "
            f"This may indicate an incorrect year suffix."
        )

    target.write_text(content, encoding="utf-8")
    print(f"Wrote {target.stat().st_size} bytes to {target}")
    return target


def fetch_dst_range(
    start_year: int, end_year: int, output_dir: Optional[Path] = None, force: bool = False
) -> list[Path]:
    """Download DST name statistics for a range of years.

    Args:
        start_year: First year to fetch (inclusive)
        end_year: Last year to fetch (inclusive)
        output_dir: Where to write the HTML files.
        force: Download even if local copies exist.

    Returns:
        List of paths to downloaded files.
    """
    target_dir = output_dir or DST_DATA_DIR
    results = []
    for year in range(start_year, end_year + 1):
        try:
            path = fetch_dst_year(year, output_dir=target_dir, force=force)
            results.append(path)
        except RequestException as exc:
            print(f"Warning: Could not fetch {year}: {exc}")
    return results


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Fetch DST newborn-name statistics")
    parser.add_argument("--year", type=int, help="Specific year to fetch (1985-2025)")
    parser.add_argument(
        "--start-year", type=int, help="Start of year range (inclusive)"
    )
    parser.add_argument(
        "--end-year", type=int, help="End of year range (inclusive)"
    )
    parser.add_argument(
        "--output-dir", type=str, help="Directory to write HTML files into"
    )
    parser.add_argument(
        "--force", action="store_true", help="Re-download even if local copies exist"
    )
    args = parser.parse_args()

    if args.year is not None:
        fetch_dst_year(
            year=args.year,
            output_dir=Path(args.output_dir) if args.output_dir else None,
            force=args.force,
        )
    elif args.start_year is not None and args.end_year is not None:
        fetch_dst_range(
            start_year=args.start_year,
            end_year=args.end_year,
            output_dir=Path(args.output_dir) if args.output_dir else None,
            force=args.force,
        )
    else:
        # Default: fetch all years
        fetch_dst_range(
            start_year=DST_MIN_YEAR,
            end_year=DST_MAX_YEAR,
            output_dir=Path(args.output_dir) if args.output_dir else None,
            force=args.force,
        )
