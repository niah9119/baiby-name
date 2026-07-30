"""Normalize Denmark (DST) name statistics to canonical CSV format.

DST publishes HTML pages with tables for each year, containing:
- Two tables: one for girls (Pigenavne), one for boys (Drengenavne)
- Columns: Nr (rank), Navn (name), Antal (count), Pr. 1 000 (ignored)

The coverage varies by year:
- 1985-1992: top 25 per sex (50 rows/year)
- 1993-2025: top 50 per sex (100 rows/year)

Total: 3,700 rows across 41 years.
"""

import re
from pathlib import Path
from typing import Optional

import pandas as pd
from bs4 import BeautifulSoup

from .config import DENMARK_COUNTRY_CODE, DST_DATA_DIR, OUTPUT_DIR

DEFAULT_OUTPUT = OUTPUT_DIR / "dst_canonical.csv"


def _parse_dst_html(content: str, year: int) -> list[dict]:
    """Parse DST HTML content for a single year.

    Args:
        content: HTML content as string
        year: The year being parsed

    Returns:
        List of records in canonical format (without country/year yet)
    """
    soup = BeautifulSoup(content, "html.parser")
    records = []

    # Find all tables - each table is for one sex
    tables = soup.find_all("table", class_="table")

    for table in tables:
        caption = table.find("caption", class_="names__headerName")
        if not caption:
            continue

        caption_text = caption.get_text(strip=True)

        # Determine sex from caption
        if "Pigenavne" in caption_text:
            sex = "Girl"
        elif "Drengenavne" in caption_text:
            sex = "Boy"
        else:
            # Unknown table type, skip
            continue

        # Parse table rows
        rows = table.find_all("tr")

        for row in rows:
            # Skip header row
            th_cells = row.find_all("th")
            if th_cells:
                continue

            # Parse data cells
            cells = row.find_all("td")
            if len(cells) < 3:
                continue

            try:
                # Columns: Nr, Navn, Antal, Pr. 1 000
                rank = int(cells[0].get_text(strip=True))
                name = cells[1].get_text(strip=True)
                count = int(cells[2].get_text(strip=True))

                # Skip empty names
                if not name:
                    continue

                records.append({
                    "name": name,
                    "sex": sex,
                    "year": year,
                    "count": count,
                    "rank": rank,
                })
            except (ValueError, IndexError):
                # Skip rows with invalid data
                continue

    return records


def normalize_dst_file(file_path: Path) -> pd.DataFrame:
    """Normalize a single DST HTML file to canonical format.

    Args:
        file_path: Path to the HTML file

    Returns:
        DataFrame in canonical format
    """
    # Extract year from filename
    match = re.search(r"dst-(\d{4})\.html$", file_path.name)
    if not match:
        raise ValueError(f"Cannot extract year from filename: {file_path.name}")
    year = int(match.group(1))

    content = file_path.read_text(encoding="utf-8")
    records = _parse_dst_html(content, year)

    df = pd.DataFrame(records)
    if df.empty:
        return pd.DataFrame(columns=["name", "country", "sex", "year", "count", "rank"])

    # Add country column
    df["country"] = DENMARK_COUNTRY_CODE

    # Ensure correct column order
    df = df[["name", "country", "sex", "year", "count", "rank"]]

    # Sort by country, sex, year, rank for consistent output
    df = df.sort_values(["country", "sex", "year", "rank"]).reset_index(drop=True)

    return df


def normalize_dst_directory(input_dir: Optional[Path] = None) -> pd.DataFrame:
    """Normalize all DST HTML files in a directory.

    Args:
        input_dir: Directory containing HTML files. Defaults to DST_DATA_DIR.

    Returns:
        DataFrame with all normalized data
    """
    input_dir = input_dir or DST_DATA_DIR

    # Find all dst-*.html files
    html_files = sorted(input_dir.glob("dst-*.html"))

    if not html_files:
        raise ValueError(f"No DST HTML files found in {input_dir}")

    all_records = []
    for file_path in html_files:
        df = normalize_dst_file(file_path)
        print(f"Processing: {file_path.name} - {len(df)} records")
        all_records.append(df)

    # Combine all dataframes
    combined_df = pd.concat(all_records, ignore_index=True)

    return combined_df


def normalize_to_csv(
    file_path: Optional[Path] = None, output: Optional[Path] = None
) -> Path:
    """Normalize the dataset and write the canonical CSV.

    Args:
        file_path: Path to HTML file or directory. If directory, processes all files.
        output: Output CSV path. Defaults to DEFAULT_OUTPUT.

    Returns:
        Path to the output CSV file.
    """
    target = Path(output) if output else DEFAULT_OUTPUT
    target.parent.mkdir(parents=True, exist_ok=True)

    input_path = Path(file_path) if file_path else DST_DATA_DIR

    if input_path.is_file():
        df = normalize_dst_file(input_path)
    else:
        df = normalize_dst_directory(input_path)

    # Write to CSV
    df.to_csv(target, index=False)

    print(f"Output written to: {target}")
    print("\nSummary:")
    print(f"  Total rows: {len(df)}")
    print(f"  Distinct names: {df['name'].nunique()}")
    if not df.empty:
        print(f"  Year range: {df['year'].min()} - {df['year'].max()}")
        print(f"  Sex categories: {', '.join(sorted(df['sex'].unique()))}")

    return target


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(
        description="Normalize DST HTML data to canonical CSV"
    )
    parser.add_argument(
        "--input-file", type=str, help="Path to DST HTML file or directory"
    )
    parser.add_argument("--output", type=str, help="Output CSV path")
    args = parser.parse_args()

    normalize_to_csv(
        file_path=Path(args.input_file) if args.input_file else None,
        output=Path(args.output) if args.output else None,
    )
