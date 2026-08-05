"""Normalize SSA, SCB, SSB, and ONS data to canonical CSV format."""

import re
from pathlib import Path
from typing import Optional

import pandas as pd

from .config import CANONICAL_CSV_PATH, ONS_DATA_DIR, SCB_DATA_DIR, SSA_DATA_DIR, SWEDEN_COUNTRY_CODE, USA_COUNTRY_CODE, GB_ENGLAND_WALES_COUNTRY_CODE


def normalize_ssa_file(file_path: Path) -> pd.DataFrame:
    """
    Normalize a single SSA data file to canonical format.

    SSA format: name,sex,count (name, sex, number of babies)

    Canonical format: name,country,sex,year,count,rank

    The canonical sex vocabulary is "Boy" and "Girl". SSA data uses "M" and "F",
    so these are mapped during normalization.

    Args:
        file_path: Path to the SSA data file (yob{year}.txt)

    Returns:
        DataFrame in canonical format
    """
    year = _extract_year_from_filename(file_path.name)

    # Read SSA data
    df = pd.read_csv(
        file_path,
        header=None,
        names=["name", "sex", "count"],
        dtype={"name": str, "sex": str, "count": int}
    )

    # Map SSA sex values to canonical vocabulary
    # SSA uses "M" for boys and "F" for girls; canonical uses "Boy"/"Girl"
    df["sex"] = df["sex"].map({"M": "Boy", "F": "Girl"})

    # Add country and year
    df["country"] = USA_COUNTRY_CODE
    df["year"] = year

    # Add rank (within each sex group)
    df["rank"] = (
        df.groupby("sex")["count"]
        .rank(method="min", ascending=False)
        .astype("Int64")
        .fillna(0)
        .astype(int)
    )

    # Reorder columns to canonical format
    df = df[["name", "country", "sex", "year", "count", "rank"]]

    return df


def _extract_year_from_filename(filename: str) -> int:
    """Extract year from filename like yob2023.txt."""
    match = re.search(r"yob(\d{4})\.txt$", filename)
    if match:
        return int(match.group(1))
    raise ValueError(f"Could not extract year from filename: {filename}")


def normalize_all_files(
    input_dir: Optional[Path] = None, output_path: Optional[Path] = None
) -> pd.DataFrame:
    """
    Normalize all SSA files in the input directory.

    Args:
        input_dir: Directory containing SSA data files
        output_path: Path to write the canonical CSV

    Returns:
        DataFrame with all normalized data
    """
    input_dir = input_dir or SSA_DATA_DIR
    output_path = output_path or CANONICAL_CSV_PATH

    # Find all SSA data files
    files = sorted(input_dir.glob("yob*.txt"))

    if not files:
        raise ValueError(f"No SSA data files found in {input_dir}")

    # Process each file and combine
    dfs = []
    for file_path in files:
        print(f"Processing: {file_path.name}")
        df = normalize_ssa_file(file_path)
        dfs.append(df)

    # Combine all dataframes
    combined_df = pd.concat(dfs, ignore_index=True)

    # Sort by country, sex, year, rank for consistent output
    combined_df = combined_df.sort_values(
        ["country", "sex", "year", "rank"], ascending=[True, True, True, True]
    )

    # Write to CSV
    output_path.parent.mkdir(parents=True, exist_ok=True)
    combined_df.to_csv(output_path, index=False)

    print(f"Canonical CSV written to: {output_path}")
    print(f"Total records: {len(combined_df)}")

    return combined_df


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Normalize SSA data to canonical CSV")
    parser.add_argument("--input-dir", type=str, help="Directory with SSA data files")
    parser.add_argument("--output", type=str, help="Output CSV path")

    args = parser.parse_args()

    input_dir = Path(args.input_dir) if args.input_dir else None
    output_path = Path(args.output) if args.output else None

    df = normalize_all_files(input_dir, output_path)
    print(f"\nSummary:")
    print(f"  Total names: {df['name'].nunique()}")
    print(f"  Country coverage: {df['country'].unique()}")
    print(f"  Sex categories: {df['sex'].unique()}")
    print(f"  Year range: {df['year'].min()} - {df['year'].max()}")


def normalize_ons_all_files(
    input_dir: Optional[Path] = None, output_path: Optional[Path] = None
) -> pd.DataFrame:
    """
    Normalize all ONS Excel files and append to the canonical CSV.

    Args:
        input_dir: Directory containing ONS Excel files
        output_path: Path to the canonical CSV (appends if exists)

    Returns:
        DataFrame with all normalized data
    """
    from . import normalize_ons

    input_dir = input_dir or ONS_DATA_DIR
    output_path = output_path or CANONICAL_CSV_PATH

    df = normalize_ons.normalize_all_files(input_dir, output_path)

    # Append to canonical CSV if it exists
    if output_path.exists() and output_path != CANONICAL_CSV_PATH:
        existing_df = pd.read_csv(output_path)
        combined_df = pd.concat([existing_df, df], ignore_index=True)
        combined_df.to_csv(output_path, index=False)
    elif output_path == CANONICAL_CSV_PATH:
        # If using default path, we're overwriting/creating the canonical CSV
        # The normalize_ons module already wrote to the file
        pass

    return df


def normalize_scb_all_files(
    input_dir: Optional[Path] = None, output_path: Optional[Path] = None
) -> pd.DataFrame:
    """
    Normalize all SCB Excel files and append to the canonical CSV.

    Args:
        input_dir: Directory containing SCB Excel files
        output_path: Path to the canonical CSV (appends if exists)

    Returns:
        DataFrame with all normalized data
    """
    from . import normalize_scb

    input_dir = input_dir or SCB_DATA_DIR
    output_path = output_path or CANONICAL_CSV_PATH

    df = normalize_scb.normalize_all_files(input_dir, output_path)

    # Append to canonical CSV if it exists
    if output_path.exists() and output_path != CANONICAL_CSV_PATH:
        existing_df = pd.read_csv(output_path)
        combined_df = pd.concat([existing_df, df], ignore_index=True)
        combined_df.to_csv(output_path, index=False)
    elif output_path == CANONICAL_CSV_PATH:
        # If using default path, we're overwriting/creating the canonical CSV
        # The normalize_scb module already wrote to the file
        pass

    return df
