"""Load canonical CSV data into PostgreSQL database."""

import csv
from pathlib import Path
from typing import Optional

import psycopg2
import sqlalchemy
from sqlalchemy import create_engine, text

from .config import CANONICAL_CSV_PATH, get_database_url


def create_database_engine(db_url: Optional[str] = None) -> sqlalchemy.Engine:
    """Create and return a database engine."""
    url = db_url or get_database_url()
    return create_engine(url)


def load_canonical_csv(
    csv_path: Optional[Path] = None,
    db_url: Optional[str] = None,
    batch_size: int = 1000,
    dry_run: bool = False,
) -> dict:
    """
    Load canonical CSV data into the database.

    This function is idempotent - running it multiple times will not duplicate rows.

    Args:
        csv_path: Path to the canonical CSV file
        db_url: Database connection URL
        batch_size: Number of rows to insert per batch
        dry_run: If True, only show what would be loaded

    Returns:
        Dictionary with statistics about the load operation
    """
    csv_path = csv_path or CANONICAL_CSV_PATH
    db_url = db_url or get_database_url()

    engine = create_engine(db_url)

    stats = {
        "total_rows": 0,
        "inserted_rows": 0,
        "skipped_rows": 0,
        "dropped_on_conflict": 0,
        "errors": [],
    }

    if not csv_path.exists():
        raise FileNotFoundError(f"Canonical CSV not found: {csv_path}")

    # Read CSV and process
    with open(csv_path, "r", newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)

        rows_to_insert = []
        for row in reader:
            stats["total_rows"] += 1

            # Check if record already exists
            exists = _name_stat_exists(engine, row)

            if exists:
                stats["skipped_rows"] += 1
                continue

            rows_to_insert.append(row)

            if len(rows_to_insert) >= batch_size:
                if not dry_run:
                    _insert_batch(engine, rows_to_insert, stats)
                rows_to_insert = []

        # Insert remaining rows
        if rows_to_insert:
            if not dry_run:
                _insert_batch(engine, rows_to_insert, stats)

    return stats


def _name_stat_exists(engine: sqlalchemy.Engine, row: dict) -> bool:
    """Check if a name_stat record already exists (idempotency check)."""
    with engine.connect() as conn:
        result = conn.execute(
            text("""
                SELECT 1 FROM name_stat ns
                JOIN given_name gn ON ns.given_name_id = gn.id
                JOIN country c ON ns.country_id = c.id
                WHERE gn.name = :name
                AND c.code = :country
                AND ns.sex = :sex
                AND ns.year = :year
            """),
            {
                "name": row["name"],
                "country": row["country"],
                "sex": row["sex"],
                "year": int(row["year"]),
            },
        ).fetchone()
        return result is not None


def _insert_batch(engine: sqlalchemy.Engine, rows: list[dict], stats: Optional[dict] = None) -> None:
    """Insert a batch of rows into the database.

    Args:
        engine: Database engine
        rows: List of rows to insert
        stats: Optional stats dict to update with actual row counts
    """
    with engine.begin() as conn:
        for row in rows:
            # Insert given_name if not exists (idempotent upsert)
            conn.execute(
                text("""
                    INSERT INTO given_name (name, created_at)
                    VALUES (:name, CURRENT_TIMESTAMP)
                    ON CONFLICT (name) DO NOTHING
                """),
                {"name": row["name"]},
            )

            # Insert country if not exists
            conn.execute(
                text("""
                    INSERT INTO country (code, name)
                    VALUES (:code, :name)
                    ON CONFLICT (code) DO NOTHING
                """),
                {"code": row["country"], "name": _get_country_name(row["country"])},
            )

            # Get IDs
            given_name_id = conn.execute(
                text("SELECT id FROM given_name WHERE name = :name"),
                {"name": row["name"]},
            ).scalar()

            country_id = conn.execute(
                text("SELECT id FROM country WHERE code = :code"),
                {"code": row["country"]},
            ).scalar()

            # Insert name_stat and track affected rows
            result = conn.execute(
                text("""
                    INSERT INTO name_stat (
                        given_name_id, country_id, sex, year, count, rank
                    )
                    VALUES (:given_name_id, :country_id, :sex, :year, :count, :rank)
                    ON CONFLICT (given_name_id, country_id, sex, year) DO NOTHING
                """),
                {
                    "given_name_id": given_name_id,
                    "country_id": country_id,
                    "sex": row["sex"],
                    "year": int(row["year"]),
                    "count": int(row["count"]),
                    "rank": int(row["rank"]),
                },
            )

            # Track actual affected rows from name_stat insert
            if stats is not None:
                # rowcount is 1 if row was inserted, 0 if conflict (DO NOTHING)
                if result.rowcount > 0:
                    stats["inserted_rows"] += 1
                else:
                    stats["dropped_on_conflict"] += 1


def _get_country_name(code: str) -> str:
    """Get full country name from ISO code."""
    country_names = {
        "US": "USA",
        "SE": "Sweden",
        "NO": "Norway",
        "DK": "Denmark",
        "GB": "England",
    }
    return country_names.get(code, code)


def load_all(dry_run: bool = False) -> dict:
    """
    Load all data from the canonical CSV.

    Args:
        dry_run: If True, only show what would be loaded

    Returns:
        Dictionary with statistics about the load operation
    """
    return load_canonical_csv(dry_run=dry_run)


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Load canonical CSV into PostgreSQL")
    parser.add_argument("--csv", type=str, help="Path to canonical CSV file")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be loaded without inserting")
    parser.add_argument("--db-url", type=str, help="Database connection URL")

    args = parser.parse_args()

    try:
        stats = load_canonical_csv(
            csv_path=Path(args.csv) if args.csv else None,
            db_url=args.db_url,
            dry_run=args.dry_run,
        )
        print("\nLoad Statistics:")
        print(f"  Total rows in CSV: {stats['total_rows']}")
        print(f"  Inserted rows: {stats['inserted_rows']}")
        print(f"  Skipped rows (already exists): {stats['skipped_rows']}")
        print(f"  Dropped on conflict (source duplicate): {stats['dropped_on_conflict']}")
        if stats["errors"]:
            print(f"  Errors: {len(stats['errors'])}")
            for error in stats["errors"][:5]:
                print(f"    - {error}")
    except Exception as e:
        print(f"Error: {e}")
        raise
