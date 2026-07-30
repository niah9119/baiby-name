"""Fetch England and Wales (ONS) baby-name statistics.

The ONS publishes baby names as a single Excel workbook covering 1996-2025.
The file is served over plain HTTP with no bot-blocking, so the fetch is a
straightforward download.

The workbook contains two sheets: Table_1 (girls' names) and Table_2 (boys' names).
Each sheet has one row per name and columns for rank and count per year (1996-2025).
"""

from pathlib import Path
from typing import Optional

import requests
from requests.exceptions import RequestException

from .config import ONS_DATA_DIR

# The URL serves the .xlsx directly -- it is not an HTML landing page.
ONS_WORKBOOK_URL = (
    "https://www.ons.gov.uk/file?uri=/peoplepopulationandcommunity/"
    "birthsdeathsandmarriages/livebirths/datasets/babynamesinenglandandwalesfrom1996/"
    "1996to2025/babynames1996to2025.xlsx"
)

ONS_WORKBOOK_FILENAME = "ons-babynames-1996-2025.xlsx"

XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

# A real workbook is ~12 MB; anything tiny is an error page rather than data.
MIN_PLAUSIBLE_SIZE = 1_000_000


def local_workbook(output_dir: Optional[Path] = None) -> Optional[Path]:
    """Return the already-downloaded workbook, or None if it is not present."""
    path = (output_dir or ONS_DATA_DIR) / ONS_WORKBOOK_FILENAME
    return path if path.exists() and path.stat().st_size > 0 else None


def download_ons_archive(
    output_dir: Optional[Path] = None, force: bool = False
) -> Path:
    """Download the ONS workbook, reusing a local copy when one exists.

    Args:
        output_dir: Where to write the workbook. Defaults to ONS_DATA_DIR.
        force: Download even when a local copy is present.

    Returns:
        Path to the workbook on disk.

    Raises:
        RequestException: the download failed, or returned something that is not a
            workbook (an HTML error page, or a suspiciously small body).
    """
    target_dir = output_dir or ONS_DATA_DIR
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / ONS_WORKBOOK_FILENAME

    if not force:
        existing = local_workbook(target_dir)
        if existing is not None:
            print(f"Using local workbook: {existing}")
            return existing

    print(f"Downloading ONS workbook from {ONS_WORKBOOK_URL}")
    try:
        response = requests.get(ONS_WORKBOOK_URL, timeout=120)
        response.raise_for_status()
    except RequestException as exc:
        raise RequestException(f"Could not download the ONS workbook: {exc}") from exc

    content_type = response.headers.get("Content-Type", "")
    if XLSX_CONTENT_TYPE not in content_type:
        raise RequestException(
            f"Expected an .xlsx workbook but got Content-Type '{content_type}'. "
            "ONS may have moved the file; check the URL in pipeline/README.md."
        )
    if len(response.content) < MIN_PLAUSIBLE_SIZE:
        raise RequestException(
            f"Downloaded only {len(response.content)} bytes, which is too small to be "
            "the workbook. Treating it as an error page rather than data."
        )

    target.write_bytes(response.content)
    print(f"Wrote {len(response.content)} bytes to {target}")
    return target


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Fetch the ONS newborn-name workbook")
    parser.add_argument(
        "--output-dir", type=str, help="Directory to write the workbook into"
    )
    parser.add_argument(
        "--force", action="store_true", help="Re-download even if a local copy exists"
    )
    args = parser.parse_args()

    download_ons_archive(
        output_dir=Path(args.output_dir) if args.output_dir else None,
        force=args.force,
    )
