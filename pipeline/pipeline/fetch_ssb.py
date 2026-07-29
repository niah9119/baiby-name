"""Fetch Norway (SSB) newborn-name statistics.

SSB table 10467 ("Born persons, by girls' name and boys' name") covers 1880 to the
present. The whole series -- 1,974 names x 146 years -- comes back in a single POST,
about 3.9 MB, so there is no paging to do: omit the `Fornavn` selection and the API
returns every name.
"""

import json
from pathlib import Path
from typing import Optional

import requests
from requests.exceptions import RequestException

from .config import SSB_DATA_DIR

SSB_TABLE_URL = "https://data.ssb.no/api/v0/en/table/10467/"

SSB_DATA_FILENAME = "ssb-10467-all.json"

# Selecting only ContentsCode leaves Fornavn and Tid unfiltered, which returns the
# complete series. "Personer" is the head count; "PersonerProsent" would be percentages.
SSB_QUERY = {
    "query": [
        {
            "code": "ContentsCode",
            "selection": {"filter": "item", "values": ["Personer"]},
        }
    ],
    "response": {"format": "json-stat2"},
}

# The real payload is ~3.9 MB; a tiny body means an error rather than data.
MIN_PLAUSIBLE_SIZE = 500_000


def local_dataset(output_dir: Optional[Path] = None) -> Optional[Path]:
    """Return the already-downloaded dataset, or None if it is not present."""
    path = (output_dir or SSB_DATA_DIR) / SSB_DATA_FILENAME
    return path if path.exists() and path.stat().st_size > 0 else None


def download_ssb_dataset(
    output_dir: Optional[Path] = None, force: bool = False
) -> Path:
    """Download the full SSB name series, reusing a local copy when one exists.

    Args:
        output_dir: Where to write the JSON. Defaults to SSB_DATA_DIR.
        force: Download even when a local copy is present.

    Returns:
        Path to the dataset on disk.

    Raises:
        RequestException: the request failed, or returned something that is not the
            expected json-stat2 payload.
    """
    target_dir = output_dir or SSB_DATA_DIR
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / SSB_DATA_FILENAME

    if not force:
        existing = local_dataset(target_dir)
        if existing is not None:
            print(f"Using local dataset: {existing}")
            return existing

    print(f"Downloading SSB table from {SSB_TABLE_URL}")
    try:
        response = requests.post(SSB_TABLE_URL, json=SSB_QUERY, timeout=180)
        response.raise_for_status()
    except RequestException as exc:
        raise RequestException(f"Could not download the SSB dataset: {exc}") from exc

    if len(response.content) < MIN_PLAUSIBLE_SIZE:
        raise RequestException(
            f"Downloaded only {len(response.content)} bytes, too small to be the full "
            "series. Treating it as an error response rather than data."
        )

    try:
        payload = response.json()
    except ValueError as exc:
        raise RequestException("SSB returned a body that is not JSON") from exc

    if "value" not in payload or "dimension" not in payload:
        raise RequestException(
            "SSB response is missing 'value'/'dimension' -- not json-stat2 as expected"
        )

    target.write_text(json.dumps(payload), encoding="utf-8")
    print(f"Wrote {target.stat().st_size} bytes to {target}")
    return target


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Fetch the SSB newborn-name series")
    parser.add_argument("--output-dir", type=str, help="Directory to write the JSON into")
    parser.add_argument(
        "--force", action="store_true", help="Re-download even if a local copy exists"
    )
    args = parser.parse_args()

    download_ssb_dataset(
        output_dir=Path(args.output_dir) if args.output_dir else None,
        force=args.force,
    )
