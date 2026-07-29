"""Fetch Sweden (SCB) newborn-name statistics.

SCB publishes the newborn-name data as a single Excel workbook covering 1998-2021,
top 100 per year and sex. It is served over plain HTTP with no bot-blocking, so the
fetch is a straightforward download.

The PxWeb statistical-database API is NOT used. Every path to the names table returns
400 (both language endpoints, GET and POST alike) while sibling levels return 200, so
the fault is on SCB's side rather than in the request. See pipeline/README.md.
"""

from pathlib import Path
from typing import Optional

import requests
from requests.exceptions import RequestException

from .config import SCB_DATA_DIR

# The URL serves the .xlsx directly -- it is not an HTML landing page.
SCB_WORKBOOK_URL = (
    "https://www.scb.se/hitta-statistik/statistik-efter-amne/"
    "befolkning-och-levnadsforhallanden/ovrigt/namnstatistik/pong/tabell-och-diagram/"
    "nyfodda--efter-namngivningsar-och-tilltalsnamn-topp-100-uppdateras-ej/"
    "namn--nyfodda-flickor-och-pojkar-19982021/"
)

SCB_WORKBOOK_FILENAME = "scb-nyfodda-1998-2021.xlsx"

XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

# A real workbook is ~570 KB; anything tiny is an error page rather than data.
MIN_PLAUSIBLE_SIZE = 100_000


def local_workbook(output_dir: Optional[Path] = None) -> Optional[Path]:
    """Return the already-downloaded workbook, or None if it is not present."""
    path = (output_dir or SCB_DATA_DIR) / SCB_WORKBOOK_FILENAME
    return path if path.exists() and path.stat().st_size > 0 else None


def download_scb_archive(
    output_dir: Optional[Path] = None, force: bool = False
) -> Path:
    """Download the SCB workbook, reusing a local copy when one exists.

    Args:
        output_dir: Where to write the workbook. Defaults to SCB_DATA_DIR.
        force: Download even when a local copy is present.

    Returns:
        Path to the workbook on disk.

    Raises:
        RequestException: the download failed, or returned something that is not a
            workbook (an HTML error page, or a suspiciously small body).
    """
    target_dir = output_dir or SCB_DATA_DIR
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / SCB_WORKBOOK_FILENAME

    if not force:
        existing = local_workbook(target_dir)
        if existing is not None:
            print(f"Using local workbook: {existing}")
            return existing

    print(f"Downloading SCB workbook from {SCB_WORKBOOK_URL}")
    try:
        response = requests.get(SCB_WORKBOOK_URL, timeout=120)
        response.raise_for_status()
    except RequestException as exc:
        raise RequestException(f"Could not download the SCB workbook: {exc}") from exc

    content_type = response.headers.get("Content-Type", "")
    if XLSX_CONTENT_TYPE not in content_type:
        raise RequestException(
            f"Expected an .xlsx workbook but got Content-Type '{content_type}'. "
            "SCB may have moved the file; check the URL in pipeline/README.md."
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

    parser = argparse.ArgumentParser(description="Fetch the SCB newborn-name workbook")
    parser.add_argument(
        "--output-dir", type=str, help="Directory to write the workbook into"
    )
    parser.add_argument(
        "--force", action="store_true", help="Re-download even if a local copy exists"
    )
    args = parser.parse_args()

    download_scb_archive(
        output_dir=Path(args.output_dir) if args.output_dir else None,
        force=args.force,
    )
