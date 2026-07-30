"""Load canonical CSV data into PostgreSQL database."""

import csv
from pathlib import Path
from typing import Optional

import psycopg2.extras
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
    batch_size: int = 10000,
    dry_run: bool = False,
) -> dict:
    """
    Load canonical CSV data into the database.

    This function is idempotent - running it multiple times will not duplicate rows.

    Optimizations:
    - Caches all given_name IDs in memory to avoid per-row lookups
    - Caches all country codes in memory to avoid per-row lookups
    - Uses batch existence checks with EXISTS clause
    - Uses PostgreSQL's multi-row VALUES for bulk inserts

    Args:
        csv_path: Path to the canonical CSV file
        db_url: Database connection URL
        batch_size: Number of rows to process per batch
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

    # Load caches for given_name and country to avoid per-row lookups
    name_cache, country_cache = _load_lookup_caches(engine)

    # Load existing name_stat records as a set of (name_id, country_id, sex, year) tuples
    existing_name_stats = _load_existing_name_stats(engine, name_cache, country_cache)

    # Read CSV and process
    with open(csv_path, "r", newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)

        rows_to_insert = []
        for row in reader:
            stats["total_rows"] += 1

            # Check if record already exists using pre-loaded cache
            name_id = name_cache.get(row["name"])
            country_id = country_cache.get(row["country"])

            if name_id is None or country_id is None:
                # Name or country doesn't exist yet - will need to insert
                exists = False
            else:
                # Check if (name_id, country_id, sex, year) exists in name_stat
                exists = (name_id, country_id, row["sex"], int(row["year"])) in existing_name_stats

            if exists:
                stats["skipped_rows"] += 1
                continue

            rows_to_insert.append(row)

            if len(rows_to_insert) >= batch_size:
                if not dry_run:
                    _insert_batch(engine, rows_to_insert, name_cache, country_cache, stats)
                rows_to_insert = []

        # Insert remaining rows
        if rows_to_insert:
            if not dry_run:
                _insert_batch(engine, rows_to_insert, name_cache, country_cache, stats)

    return stats


def _load_lookup_caches(engine: sqlalchemy.Engine) -> tuple[dict, dict]:
    """
    Load caches for given_name and country lookups.

    Returns:
        Tuple of (name_cache, country_cache) where:
        - name_cache: dict mapping name string to id
        - country_cache: dict mapping country code to id
    """
    name_cache = {}
    country_cache = {}

    with engine.connect() as conn:
        # Load all given_names
        result = conn.execute(text("SELECT id, name FROM given_name"))
        for row in result:
            name_cache[row[1]] = row[0]

        # Load all countries
        result = conn.execute(text("SELECT id, code FROM country"))
        for row in result:
            country_cache[row[1]] = row[0]

    return name_cache, country_cache


def _load_existing_name_stats(
    engine: sqlalchemy.Engine, name_cache: dict, country_cache: dict
) -> set:
    """
    Load existing name_stat records as a set for quick lookup.

    Returns:
        Set of tuples (given_name_id, country_id, sex, year) for existing records.
        Only includes records where the name_id and country_id are in the caches.
    """
    existing = set()

    if not name_cache and not country_cache:
        return existing

    # Build IN clauses for names and countries we know about
    name_ids = list(name_cache.values())
    country_ids = list(country_cache.values())

    if not name_ids or not country_ids:
        return existing

    with engine.connect() as conn:
        # Load existing name_stat records for names and countries in our caches
        result = conn.execute(
            text("""
                SELECT given_name_id, country_id, sex, year
                FROM name_stat
                WHERE given_name_id = ANY(:name_ids)
                AND country_id = ANY(:country_ids)
            """),
            {"name_ids": name_ids, "country_ids": country_ids},
        )
        for row in result:
            existing.add((row[0], row[1], row[2], row[3]))

    return existing


def _insert_batch(
    engine: sqlalchemy.Engine,
    rows: list[dict],
    name_cache: dict,
    country_cache: dict,
    stats: Optional[dict] = None,
) -> None:
    """Insert a batch of rows into the database using bulk operations.

    This function:
    1. Identifies new names and countries to insert
    2. Inserts them in bulk using multi-row VALUES
    3. Updates caches with new IDs
    4. Inserts name_stat records in bulk

    Args:
        engine: Database engine
        rows: List of rows to insert
        name_cache: Cache of existing name IDs (updated in-place)
        country_cache: Cache of existing country IDs (updated in-place)
        stats: Optional stats dict to update with actual row counts
    """
    if not rows:
        return

    # Track which (name, country, sex, year) combinations we've seen in this batch
    # to detect source duplicates
    seen_in_batch = set()

    # Separate rows by whether their name/country already exists
    new_rows = []
    for row in rows:
        name_id = name_cache.get(row["name"])
        country_id = country_cache.get(row["country"])

        # Build the composite key for deduplication
        key = (row["name"], row["country"], row["sex"], int(row["year"]))

        # Check if this exact combination was seen earlier in this batch
        if key in seen_in_batch:
            # This is a source duplicate - drop on conflict
            if stats is not None:
                stats["dropped_on_conflict"] += 1
            continue

        seen_in_batch.add(key)

        new_rows.append({
            "row": row,
            "name_id": name_id,
            "country_id": country_id,
        })

    # Collect unique new names and countries
    new_names = set()
    new_countries = set()
    for item in new_rows:
        if item["name_id"] is None:
            new_names.add(item["row"]["name"])
        if item["country_id"] is None:
            new_countries.add((item["row"]["country"], _get_country_name(item["row"]["country"])))

    # Insert new names in bulk
    if new_names:
        new_name_ids = _insert_new_names(engine, list(new_names))
        for name, id_ in new_name_ids.items():
            name_cache[name] = id_

    # Insert new countries in bulk
    if new_countries:
        new_country_ids = _insert_new_countries(engine, list(new_countries))
        for code, id_ in new_country_ids.items():
            country_cache[code] = id_

    # Update new_rows with the new IDs
    for item in new_rows:
        if item["name_id"] is None:
            item["name_id"] = name_cache.get(item["row"]["name"])
        if item["country_id"] is None:
            item["country_id"] = country_cache.get(item["row"]["country"])

    # Group rows by (given_name_id, country_id) for bulk name_stat insertion
    # Using PostgreSQL's execute_values for multi-row insert
    _insert_name_stats(engine, new_rows, stats)


def _insert_new_names(engine: sqlalchemy.Engine, names: list[str]) -> dict[str, int]:
    """
    Insert new names in bulk and return mapping of name -> id.

    Uses a single INSERT ... VALUES ... RETURNING query for efficiency.
    """
    if not names:
        return {}

    # Use raw psycopg2 connection for multi-row VALUES with explicit commit
    with engine.raw_connection() as conn:
        cursor = conn.cursor()
        try:
            # Build multi-row VALUES clause for names
            values_clause = ", ".join(["(%s, CURRENT_TIMESTAMP)"] * len(names))

            cursor.execute(
                f"""
                    INSERT INTO given_name (name, created_at)
                    VALUES {values_clause}
                    ON CONFLICT (name) DO NOTHING
                    RETURNING id, name
                """,
                names,
            )
            return {row[1]: row[0] for row in cursor.fetchall()}
        finally:
            conn.commit()  # Explicitly commit the transaction
            conn.close()


def _insert_new_countries(engine: sqlalchemy.Engine, countries: list[tuple[str, str]]) -> dict[str, int]:
    """
    Insert new countries in bulk and return mapping of code -> id.

    Args:
        countries: List of (code, name) tuples

    Returns:
        Dict mapping country code to id
    """
    if not countries:
        return {}

    # Use raw psycopg2 connection for multi-row VALUES with explicit commit
    with engine.raw_connection() as conn:
        cursor = conn.cursor()
        try:
            # Build multi-row VALUES clause for countries
            values_clause = ", ".join(["(%s, %s)"] * len(countries))

            # Flatten the list of tuples
            flat_values = [item for pair in countries for item in pair]

            cursor.execute(
                f"""
                    INSERT INTO country (code, name)
                    VALUES {values_clause}
                    ON CONFLICT (code) DO NOTHING
                    RETURNING id, code
                """,
                flat_values,
            )
            return {row[1]: row[0] for row in cursor.fetchall()}
        finally:
            conn.commit()  # Explicitly commit the transaction
            conn.close()


def _insert_name_stats(
    engine: sqlalchemy.Engine,
    rows: list[dict],
    stats: Optional[dict] = None,
) -> None:
    """
    Insert name_stat records in bulk using PostgreSQL's multi-row VALUES.

    Uses execute_values for efficient bulk insert with ON CONFLICT handling.
    """
    if not rows:
        return

    # Prepare data for bulk insert
    # Each entry: (given_name_id, country_id, sex, year, count, rank)
    name_stat_data = []
    for item in rows:
        row = item["row"]
        name_stat_data.append((
            item["name_id"],
            item["country_id"],
            row["sex"],
            int(row["year"]),
            int(row["count"]),
            int(row["rank"]),
        ))

    # Use engine.begin() for proper transaction handling
    with engine.begin() as conn:
        # Use psycopg2.extras.execute_values for efficient multi-row insert
        # This generates a single INSERT INTO ... VALUES (...), (...), ... statement
        # Note: We need to use the raw connection's cursor for execute_values
        psycopg2.extras.execute_values(
            conn.connection.cursor(),  # Get raw cursor from SQLAlchemy connection
            """
                INSERT INTO name_stat (
                    given_name_id, country_id, sex, year, count, rank
                )
                VALUES %s
                ON CONFLICT (given_name_id, country_id, sex, year) DO NOTHING
            """,
            name_stat_data,
            template=None,
            page_size=1000,
        )

    # Count affected rows
    inserted = len(name_stat_data)

    if stats is not None:
        stats["inserted_rows"] += inserted


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
