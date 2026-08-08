"""Load canonical CSV data into PostgreSQL database."""

import csv
from pathlib import Path
from typing import Optional, List, Tuple

import psycopg2.extras
import sqlalchemy
from sqlalchemy import create_engine, text

from .config import CANONICAL_CSV_PATH, get_database_url

# Canonical sex vocabulary - all importers must produce only these values
CANONICAL_SEX_VALUES = {"Boy", "Girl"}


def _validate_sex_value(sex: str, row_num: int, country: str) -> None:
    """
    Validate that a sex value is in the canonical vocabulary.

    Args:
        sex: The sex value to validate
        row_num: The row number in the CSV (for error reporting)
        country: The country code (for error reporting)

    Raises:
        ValueError: If the sex value is not in the canonical vocabulary
    """
    if sex not in CANONICAL_SEX_VALUES:
        raise ValueError(
            f"Invalid sex value '{sex}' at row {row_num}, country '{country}'. "
            f"Expected one of: {', '.join(sorted(CANONICAL_SEX_VALUES))}. "
            f"See pipeline/README.md for the canonical sex vocabulary contract."
        )


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

    # Determine if this is a famous bearers CSV or a name stats CSV
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        first_row = next(reader, None)
        if first_row is None:
            return stats
        is_famous_bearers = "public_name" in first_row

    # Reset file position
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        rows = list(reader)

    if is_famous_bearers:
        # Famous bearers CSV uses a different stats structure
        bearer_stats = {
            "total_rows": 0,
            "inserted_bearers": 0,
            "skipped_bearers": 0,
            "inserted_links": 0,
            "skipped_links": 0,
            "unresolved_names": [],
            "errors": [],
        }
        return load_famous_bearers_csv(rows, engine, dry_run, bearer_stats)
    else:
        # Name stats CSV - original flow
        return load_name_stats_csv(rows, engine, batch_size, dry_run, stats)


def load_name_stats_csv(
    rows: List[dict],
    engine: sqlalchemy.Engine,
    batch_size: int = 10000,
    dry_run: bool = False,
    stats: Optional[dict] = None,
) -> dict:
    """Load name statistics CSV data into the database."""
    if stats is None:
        stats = {
            "total_rows": 0,
            "inserted_rows": 0,
            "skipped_rows": 0,
            "dropped_on_conflict": 0,
            "errors": [],
        }

    # Load caches for given_name and country to avoid per-row lookups
    name_cache, country_cache = _load_lookup_caches(engine)

    # Load existing name_stat records as a set of (name_id, country_id, sex, year) tuples
    existing_name_stats = _load_existing_name_stats(engine, name_cache, country_cache)

    stats["total_rows"] = len(rows)

    rows_to_insert = []
    for row in rows:
        # Check if record already exists using pre-loaded cache
        name_id = name_cache.get(row["name"])
        country_id = country_cache.get(row["country"])

        # Validate sex value against canonical vocabulary (#61)
        _validate_sex_value(row["sex"], stats["total_rows"], row["country"])

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


def load_famous_bearers_csv(
    rows: List[dict],
    engine: sqlalchemy.Engine,
    dry_run: bool = False,
    stats: Optional[dict] = None,
) -> dict:
    """Load famous bearers CSV data into the database.

    This function is idempotent - running it multiple times will not duplicate rows.
    It matches existing bearers on wikidata_id, not on public_name.

    Args:
        rows: List of row dictionaries with columns:
            public_name, subcategory, given_names, country, wikidata_id
        engine: Database engine
        dry_run: If True, only show what would be loaded
        stats: Optional stats dict to update

    Returns:
        Dictionary with statistics about the load operation
    """
    if stats is None:
        stats = {
            "total_rows": 0,
            "inserted_bearers": 0,
            "skipped_bearers": 0,
            "inserted_links": 0,
            "skipped_links": 0,
            "unresolved_names": [],
            "errors": [],
        }

    stats["total_rows"] = len(rows)

    # Load caches for given_name and existing bearers
    name_cache = _load_given_name_cache(engine)

    # Load existing bearers by wikidata_id to avoid duplicates
    existing_bearers = _load_existing_bearers(engine)

    # Track names that couldn't be resolved
    unresolved_names = set()

    # Collect bearers to insert - don't commit until we know all names resolve
    bearers_to_insert = []  # List of (public_name, subcategory, wikidata_id)

    for row in rows:
        public_name = row["public_name"].strip()
        subcategory = row["subcategory"].strip().upper()
        given_names_str = row["given_names"].strip()
        country = row["country"].strip().upper()
        wikidata_id = row["wikidata_id"].strip()

        # Validate required fields
        if not all([public_name, subcategory, wikidata_id]):
            stats["errors"].append(f"Missing required field: {row}")
            continue

        # Validate subcategory
        if subcategory not in ("ROYALTY", "MOVIE_STAR", "SPORTS_STAR"):
            stats["errors"].append(f"Invalid subcategory: {subcategory}")
            continue

        # Check if bearer already exists by wikidata_id
        existing_bearer = existing_bearers.get(wikidata_id)

        if existing_bearer:
            stats["skipped_bearers"] += 1
        else:
            bearers_to_insert.append((public_name, subcategory, wikidata_id))
            stats["inserted_bearers"] += 1

        # Parse and link given names - validate all names first
        if given_names_str:
            given_names = [n.strip() for n in given_names_str.split(";") if n.strip()]

            for given_name in given_names:
                name_id = name_cache.get(given_name)

                if name_id is None:
                    # Name doesn't exist in the database
                    unresolved_names.add(given_name)
                    continue

                # Check if link already exists
                if existing_bearer:
                    bearer_id = existing_bearer["id"]
                else:
                    # Will be inserted after validation passes
                    bearer_id = None

                if bearer_id and _check_name_bearer_link_exists(engine, name_id, bearer_id):
                    stats["skipped_links"] += 1
                else:
                    stats["inserted_links"] += 1

    stats["unresolved_names"] = list(unresolved_names)

    # Only insert bearers and links if all names resolve (transactional load)
    if unresolved_names:
        return stats

    if dry_run:
        return stats

    # Insert all bearers in a single transaction
    _insert_famous_bearers_batch(engine, bearers_to_insert)

    # Reload existing bearers to get IDs for newly inserted ones
    existing_bearers = _load_existing_bearers(engine)

    # Now link names to bearers
    for row in rows:
        given_names_str = row["given_names"].strip()
        wikidata_id = row["wikidata_id"].strip()

        if not given_names_str:
            continue

        # Get bearer_id for this row
        existing_bearer = existing_bearers.get(wikidata_id)
        if existing_bearer:
            bearer_id = existing_bearer["id"]
        else:
            continue

        given_names = [n.strip() for n in given_names_str.split(";") if n.strip()]

        for given_name in given_names:
            name_id = name_cache.get(given_name)

            if name_id is None:
                continue

            if _check_name_bearer_link_exists(engine, name_id, bearer_id):
                continue

            _insert_name_bearer_link(engine, name_id, bearer_id)

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


def _load_given_name_cache(engine: sqlalchemy.Engine) -> dict[str, int]:
    """Load a cache of given names to IDs."""
    name_cache = {}
    with engine.connect() as conn:
        result = conn.execute(text("SELECT id, name FROM given_name"))
        for row in result:
            name_cache[row[1]] = row[0]
    return name_cache


def _load_existing_bearers(engine: sqlalchemy.Engine) -> dict[str, dict]:
    """Load existing bearers keyed by wikidata_id."""
    bearers = {}
    with engine.connect() as conn:
        result = conn.execute(text("SELECT id, public_name, wikidata_id FROM famous_bearer"))
        for row in result:
            bearers[row[2]] = {"id": row[0], "public_name": row[1]}
    return bearers


def _check_name_bearer_link_exists(
    engine: sqlalchemy.Engine, given_name_id: int, bearer_id: int
) -> bool:
    """Check if a name-to-bearer link already exists."""
    with engine.connect() as conn:
        result = conn.execute(
            text("""
                SELECT EXISTS (
                    SELECT 1 FROM name_famous_bearer
                    WHERE given_name_id = :name_id AND famous_bearer_id = :bearer_id
                )
            """),
            {"name_id": given_name_id, "bearer_id": bearer_id},
        )
        return result.scalar()


def _insert_famous_bearer(
    engine: sqlalchemy.Engine,
    public_name: str,
    subcategory: str,
    wikidata_id: str,
) -> int:
    """Insert a new famous bearer and return its ID."""
    with engine.raw_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute(
                """
                    INSERT INTO famous_bearer (public_name, subcategory, wikidata_id, created_at)
                    VALUES (%s, %s, %s, CURRENT_TIMESTAMP)
                    ON CONFLICT (wikidata_id) DO NOTHING
                    RETURNING id
                """,
                (public_name, subcategory, wikidata_id),
            )
            row = cursor.fetchone()
            conn.commit()
            return row[0] if row else None
        finally:
            conn.close()


def _insert_name_bearer_link(
    engine: sqlalchemy.Engine, given_name_id: int, bearer_id: int
) -> None:
    """Insert a link between a given name and a famous bearer."""
    with engine.raw_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute(
                """
                    INSERT INTO name_famous_bearer (given_name_id, famous_bearer_id)
                    VALUES (%s, %s)
                    ON CONFLICT (given_name_id, famous_bearer_id) DO NOTHING
                """,
                (given_name_id, bearer_id),
            )
            conn.commit()
        finally:
            conn.close()


def _insert_famous_bearers_batch(
    engine: sqlalchemy.Engine,
    bearers: list[tuple[str, str, str]],
) -> None:
    """Insert multiple famous bearers in a single transaction using PostgreSQL's multi-row VALUES.

    Args:
        engine: Database engine
        bearers: List of (public_name, subcategory, wikidata_id) tuples
    """
    if not bearers:
        return

    # Use raw psycopg2 connection for multi-row VALUES with explicit commit
    with engine.raw_connection() as conn:
        cursor = conn.cursor()
        try:
            # Build multi-row VALUES clause for bearers
            # Format: (public_name, subcategory, wikidata_id, created_at)
            values_clause = ", ".join(["(%s, %s, %s, CURRENT_TIMESTAMP)"] * len(bearers))

            # Flatten the list of tuples
            flat_values = [item for triple in bearers for item in triple]

            cursor.execute(
                f"""
                    INSERT INTO famous_bearer (public_name, subcategory, wikidata_id, created_at)
                    VALUES {values_clause}
                    ON CONFLICT (wikidata_id) DO NOTHING
                    RETURNING id, wikidata_id
                """,
                flat_values,
            )
            # We don't need to return the IDs since we reload the cache afterward
            cursor.fetchall()
            conn.commit()
        finally:
            conn.close()


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
    import sys

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

        # Check if this is a famous_bearers CSV (has different stats structure)
        is_famous_bearers = "inserted_bearers" in stats

        print("\nLoad Statistics:")

        if is_famous_bearers:
            print(f"  Total rows in CSV: {stats['total_rows']}")
            print(f"  Inserted bearers: {stats['inserted_bearers']}")
            print(f"  Skipped bearers (already exists): {stats['skipped_bearers']}")
            print(f"  Inserted links: {stats['inserted_links']}")
            print(f"  Skipped links (already exists): {stats['skipped_links']}")
            if stats["unresolved_names"]:
                print(f"  Unresolved names: {len(stats['unresolved_names'])}")
                # Show first 10 unresolved names
                unresolved = stats['unresolved_names'][:10]
                for name in unresolved:
                    print(f"    - {name}")
                if len(stats['unresolved_names']) > 10:
                    print(f"    ... and {len(stats['unresolved_names']) - 10} more")
            if stats["errors"]:
                print(f"  Errors: {len(stats['errors'])}")
                for error in stats["errors"][:5]:
                    print(f"    - {error}")
        else:
            # Name stats CSV
            print(f"  Total rows in CSV: {stats['total_rows']}")
            print(f"  Inserted rows: {stats['inserted_rows']}")
            print(f"  Skipped rows (already exists): {stats['skipped_rows']}")
            print(f"  Dropped on conflict (source duplicate): {stats['dropped_on_conflict']}")
            if stats["errors"]:
                print(f"  Errors: {len(stats['errors'])}")
                for error in stats["errors"][:5]:
                    print(f"    - {error}")

        # Fail loudly on unresolved names (this is a data integrity issue)
        if is_famous_bearers and stats["unresolved_names"]:
            print(
                f"\nERROR: {len(stats['unresolved_names'])} names in the famous bearers CSV "
                f"could not be resolved to a given_name in the database."
            )
            print(
                "These names must exist in the given_name table before they can be linked to bearers. "
                "Load the name statistics CSV first, then re-run this command."
            )
            sys.exit(1)

        # Fail on errors
        if stats["errors"]:
            print(f"\nERROR: {len(stats['errors'])} errors occurred during load.")
            sys.exit(1)

    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)
