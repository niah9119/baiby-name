"""Normalize Norway (SSB) name statistics to the canonical CSV format.

SSB returns json-stat2: a flat `value` list addressed by the dimension sizes. Two
details of the encoding matter and are easy to get wrong:

1. There is no sex dimension. Sex is the FIRST CHARACTER of the `Fornavn` code --
   "1" for girls, "2" for boys. Ignoring it merges the sexes into one series.
2. The codes are ASCII-only: "Z2" stands for "Ø" and "Z3" for "Å", so `1BJZ2RG` is
   Bjørg. Never derive the display name from the code -- use `category.label`, which
   carries the properly accented form.

The source has no rank, so ranks are computed per year and sex, with ties sharing a
rank the way the SSA and SCB importers do.
"""

import json
from pathlib import Path
from typing import Optional

import pandas as pd

from .config import NORWAY_COUNTRY_CODE, OUTPUT_DIR, SSB_DATA_DIR
from .fetch_ssb import SSB_DATA_FILENAME

GIRL_PREFIX = "1"
BOY_PREFIX = "2"

DEFAULT_OUTPUT = OUTPUT_DIR / "ssb_canonical.csv"


def _sex_from_code(code: str) -> Optional[str]:
    """Map an SSB name code to a canonical sex, or None if the prefix is unknown."""
    if code.startswith(GIRL_PREFIX):
        return "Girl"
    if code.startswith(BOY_PREFIX):
        return "Boy"
    return None


def _records_from_payload(payload: dict) -> list[dict]:
    """Turn a json-stat2 payload into canonical records.

    The `value` list is addressed as value[name_index * n_years + year_index], following
    the dimension order reported by the payload itself rather than a hardcoded guess.
    """
    dimension = payload["dimension"]
    order = payload["id"]
    sizes = payload["size"]
    values = payload["value"]

    name_axis = order.index("Fornavn")
    year_axis = order.index("Tid")
    n_years = sizes[year_axis]

    # A value's position moves by this much per step along each axis.
    name_stride = 1
    for axis in range(name_axis + 1, len(sizes)):
        name_stride *= sizes[axis]
    year_stride = 1
    for axis in range(year_axis + 1, len(sizes)):
        year_stride *= sizes[axis]

    name_cat = dimension["Fornavn"]["category"]
    year_cat = dimension["Tid"]["category"]
    labels = name_cat["label"]

    records = []
    for code, name_index in name_cat["index"].items():
        sex = _sex_from_code(code)
        if sex is None:
            continue
        # label carries the accented form; the code is ASCII-mangled (Z2 -> O-slash).
        name = labels[code]

        for year, year_index in year_cat["index"].items():
            count = values[name_index * name_stride + year_index * year_stride]
            # Years in which a name was not registered come back null; they are not zeros.
            if count is None or count == 0:
                continue
            records.append(
                {
                    "name": name,
                    "country": NORWAY_COUNTRY_CODE,
                    "sex": sex,
                    "year": int(year),
                    "count": int(count),
                }
            )
    return records


def normalize_ssb_dataset(file_path: Optional[Path] = None) -> pd.DataFrame:
    """Normalize the SSB json-stat2 dataset to the canonical shape.

    Ranks are computed per year and sex from the counts. Ties share a rank ("min"
    method), matching the SSA and SCB importers.
    """
    path = file_path or (SSB_DATA_DIR / SSB_DATA_FILENAME)
    payload = json.loads(Path(path).read_text(encoding="utf-8"))

    records = _records_from_payload(payload)
    df = pd.DataFrame(records)
    if df.empty:
        return pd.DataFrame(columns=["name", "country", "sex", "year", "count", "rank"])

    df["rank"] = (
        df.groupby(["year", "sex"])["count"]
        .rank(method="min", ascending=False)
        .astype(int)
    )
    df = df.sort_values(["year", "sex", "rank", "name"]).reset_index(drop=True)
    return df[["name", "country", "sex", "year", "count", "rank"]]


def normalize_to_csv(
    file_path: Optional[Path] = None, output: Optional[Path] = None
) -> Path:
    """Normalize the dataset and write the canonical CSV."""
    df = normalize_ssb_dataset(file_path)
    target = Path(output) if output else DEFAULT_OUTPUT
    target.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(target, index=False)

    print(f"Output written to: {target}")
    print("\nSummary:")
    print(f"  Total rows: {len(df)}")
    print(f"  Distinct names: {df['name'].nunique()}")
    if not df.empty:
        print(f"  Year range: {df['year'].min()} - {df['year'].max()}")
    return target


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(
        description="Normalize the SSB dataset to canonical CSV"
    )
    parser.add_argument("--input-file", type=str, help="Path to the SSB json-stat2 file")
    parser.add_argument("--output", type=str, help="Output CSV path")
    args = parser.parse_args()

    normalize_to_csv(
        file_path=Path(args.input_file) if args.input_file else None,
        output=Path(args.output) if args.output else None,
    )
