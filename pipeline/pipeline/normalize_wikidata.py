"""Normalize Wikidata famous bearer data to canonical CSV format.

This module converts the raw Wikidata CSV (produced by fetch_wikidata.py) into
the canonical format expected by load.py:

```
public_name,subcategory,given_names,country,wikidata_id
Zlatan Ibrahimović,SPORTS_STAR,Zlatan,SE,Q550
Ole Gunnar Solskjær,SPORTS_STAR,Gunnar;Ole,NO,Q170581
Henrik Larsson,SPORTS_STAR,Henrik;Henke,SE,Q212689
```

The canonical format:
- public_name: The person's public/famous name (preserved exactly as in Wikidata)
- subcategory: ROYALTY, MOVIE_STAR, or SPORTS_STAR
- given_names: Semicolon-separated list of given names (including aliases like "Leo")
- country: ISO 3166-1 alpha-2 country code (US, GB, SE, NO, DK)
- wikidata_id: The Wikidata entity ID (e.g., Q615)
"""

import csv
from pathlib import Path
from typing import Optional


def normalize_wikidata(
    input_file: Path, output_file: Optional[Path] = None
) -> Path:
    """Normalize Wikidata famous bearer data to canonical CSV format.

    Args:
        input_file: Path to the raw Wikidata CSV file (from fetch_wikidata.py).
        output_file: Path to write the normalized CSV. Defaults to
                     pipeline/data/output/wikidata_canonical.csv.

    Returns:
        Path to the normalized CSV file.

    Raises:
        FileNotFoundError: If the input file does not exist.
        ValueError: If the input file is missing required columns.
    """
    if not input_file.exists():
        raise FileNotFoundError(f"Input file not found: {input_file}")

    # Default output path
    if output_file is None:
        output_dir = input_file.parent.parent / "output"
        output_dir.mkdir(parents=True, exist_ok=True)
        output_file = output_dir / "wikidata_canonical.csv"

    # Read input CSV
    with open(input_file, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        rows = list(reader)

    # Check required columns
    required_columns = {"public_name", "subcategory", "given_names", "country", "wikidata_id"}
    if not required_columns.issubset(set(rows[0].keys())):
        missing = required_columns - set(rows[0].keys())
        raise ValueError(f"Input file missing required columns: {missing}")

    # Normalize and deduplicate
    seen = set()
    normalized_rows = []

    for row in rows:
        # Extract fields
        public_name = row["public_name"].strip()
        subcategory = row["subcategory"].strip().upper()
        given_names = row["given_names"].strip()
        country = row["country"].strip().upper()
        wikidata_id = row["wikidata_id"].strip()

        # Skip if missing required fields
        if not all([public_name, subcategory, wikidata_id]):
            continue

        # Validate subcategory
        if subcategory not in ("ROYALTY", "MOVIE_STAR", "SPORTS_STAR"):
            continue

        # Validate country
        if country not in ("US", "GB", "SE", "NO", "DK"):
            continue

        # Deduplicate by (public_name, subcategory, wikidata_id)
        key = (public_name, subcategory, wikidata_id)
        if key in seen:
            continue
        seen.add(key)

        normalized_rows.append({
            "public_name": public_name,
            "subcategory": subcategory,
            "given_names": given_names,
            "country": country,
            "wikidata_id": wikidata_id,
        })

    # Write output CSV
    with open(output_file, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["public_name", "subcategory", "given_names", "country", "wikidata_id"]
        )
        writer.writeheader()
        writer.writerows(normalized_rows)

    print(f"Wrote {len(normalized_rows)} rows to {output_file}")
    return output_file


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Normalize Wikidata famous bearer data")
    parser.add_argument(
        "--input-file",
        type=str,
        required=True,
        help="Path to the raw Wikidata CSV file",
    )
    parser.add_argument(
        "--output-file",
        type=str,
        help="Path to write the normalized CSV file",
    )
    args = parser.parse_args()

    input_path = Path(args.input_file)
    output_path = Path(args.output_file) if args.output_file else None

    result = normalize_wikidata(input_path, output_path)
    print(f"Done. Output written to {result}")
