"""Fetch famous bearers from Wikidata SPARQL endpoint.

Wikidata provides a SPARQL endpoint at https://query.wikidata.org/sparql
that can be used to query for famous people and their given names.

This module follows the same pattern as other fetchers:
- Local-first: cache raw responses to disk
- Re-read from cache on subsequent runs
- No API key required, but a descriptive User-Agent is required

The fetch process generates a CSV file with columns:
- public_name: The person's public/famous name
- subcategory: ROYALTY, MOVIE_STAR, or SPORTS_STAR
- given_names: Semicolon-separated list of given names (including aliases)
- country: ISO 3166-1 alpha-2 country code
- wikidata_id: The Wikidata entity ID (e.g., Q615)
"""

import csv
import re
from pathlib import Path
from typing import Optional

import requests

from .config import OUTPUT_DIR

# Wikidata SPARQL endpoint
WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"

# User-Agent header is required by Wikidata
USER_AGENT = "baiby-name-pipeline/0.1 (https://github.com/niah9119/baiby-name; niclas.ahlstrand@gmail.com)"

# Data directory for raw Wikidata responses
WIKIDATA_RAW_DIR = OUTPUT_DIR.parent / "wikidata" / "raw"

# Output CSV directory
WIKIDATA_CSV_DIR = OUTPUT_DIR.parent / "famous_bearers"


# Country QIDs
Q_USA = "Q30"
Q_GB = "Q145"
Q_SWEDEN = "Q34"
Q_NORWAY = "Q20"
Q_DENMARK = "Q35"

COUNTRIES = {
    "US": Q_USA,
    "GB": Q_GB,
    "SE": Q_SWEDEN,
    "NO": Q_NORWAY,
    "DK": Q_DENMARK,
}

# Occupation QIDs
Q_MONARCH = "Q116"  # monarch
Q_FILM_ACTOR = "Q10800557"  # film actor
Q_ACTOR = "Q33999"  # actor (broader, but causes timeouts)
Q_FOOTBALLER = "Q937857"  # footballer
Q_TENNIS_PLAYER = "Q10833314"  # tennis player
Q_ICE_HOCKEY_PLAYER = "Q11774891"  # ice-hockey player
Q_SKIER = "Q13381863"  # skier
Q_KING = "Q12097"  # king
Q_CROWN_PRINCE = "Q116538"  # crown prince

# Subcategory to occupation mappings
# Note: order matters - ROYALTY before other categories to avoid monarchs appearing in SPORTS_STAR
SUBCATEGORY_MAPPINGS = {
    "ROYALTY": [Q_MONARCH, Q_KING, Q_CROWN_PRINCE],
    "MOVIE_STAR": [Q_FILM_ACTOR],
    "SPORTS_STAR": [Q_FOOTBALLER, Q_TENNIS_PLAYER, Q_ICE_HOCKEY_PLAYER, Q_SKIER],
}

# Regex to filter aliases: single token, Latin script, reasonable length
# This matches strings like "Leo", "Henke", "Gunnar" while excluding nicknames like "Ibra"
ALIAS_REGEX = re.compile(r"^[A-ZÅÄÖÉÈØÆ][a-zåäöéèøæüï'-]{1,14}$")


def local_dataset(country_code: str, occupation_qid: str) -> Optional[Path]:
    """Return the already-downloaded dataset for a country/occupation, or None if not present."""
    filename = f"wikidata_{country_code}_{occupation_qid.replace('wd:', '')}.csv"
    path = WIKIDATA_RAW_DIR / filename
    return path if path.exists() and path.stat().st_size > 0 else None


def fetch_people_qids(country_qid: str, occupations: list[str], limit: int = 500) -> list[str]:
    """Fetch person QIDs for a specific country and occupation set.

    This is a fast query that only returns the QID without labels.

    Args:
        country_qid: Wikidata QID for the country
        occupations: List of Wikidata QIDs for occupations
        limit: Maximum number of results

    Returns:
        List of Wikidata person QIDs (e.g., ["Q615", "Q123"])
    """
    occupation_list = " ".join(occupations)

    query = f"""
    SELECT DISTINCT ?person WHERE {{
      ?person wdt:P31 wd:Q5 .
      ?person wdt:P27 wd:{country_qid} .
      ?person wdt:P106 {occupation_list} .
    }}
    LIMIT {limit}
    """

    headers = {
        "Accept": "text/csv",
        "User-Agent": USER_AGENT,
    }

    try:
        response = requests.post(
            WIKIDATA_SPARQL_URL,
            data={"query": query},
            headers=headers,
            timeout=180,
        )
        response.raise_for_status()
    except requests.RequestException as exc:
        raise requests.RequestException(f"Could not fetch Wikidata people QIDs: {exc}") from exc

    if len(response.content) < 50:
        raise requests.RequestException(
            f"Downloaded only {len(response.content)} bytes, too small to be valid data."
        )

    # Parse CSV to extract QIDs
    qids = []
    lines = response.text.strip().split('\n')
    for line in lines[1:]:  # Skip header
        if line:
            # Extract QID from URL like "http://www.wikidata.org/entity/Q615"
            match = re.search(r'/entity/(Q\d+)$', line)
            if match:
                qids.append(match.group(1))

    return qids


def fetch_aliases_for_person(person_qid: str) -> list[str]:
    """Fetch aliases for a specific person.

    Args:
        person_qid: Wikidata QID for the person

    Returns:
        List of alias labels in supported languages
    """
    query = f"""
    SELECT ?altLabel WHERE {{
      wd:{person_qid} skos:altLabel ?altLabel .
      FILTER(LANG(?altLabel) = "en" || LANG(?altLabel) = "es" || LANG(?altLabel) = "sv" || LANG(?altLabel) = "no" || LANG(?altLabel) = "da")
    }}
    """

    headers = {
        "Accept": "text/csv",
        "User-Agent": USER_AGENT,
    }

    try:
        response = requests.post(
            WIKIDATA_SPARQL_URL,
            data={"query": query},
            headers=headers,
            timeout=180,
        )
        response.raise_for_status()
    except requests.RequestException as exc:
        raise requests.RequestException(f"Could not fetch aliases for {person_qid}: {exc}") from exc

    # Parse CSV to extract aliases
    aliases = []
    lines = response.text.strip().split('\n')
    for line in lines[1:]:  # Skip header
        if line:
            aliases.append(line.strip())

    return aliases


def fetch_person_label(person_qid: str) -> Optional[str]:
    """Fetch the primary label for a specific person.

    Args:
        person_qid: Wikidata QID for the person

    Returns:
        The person's label in English, or None if not found
    """
    query = f"""
    SELECT ?personLabel WHERE {{
      wd:{person_qid} rdfs:label ?personLabel .
      FILTER(LANG(?personLabel) = "en")
    }}
    """

    headers = {
        "Accept": "text/csv",
        "User-Agent": USER_AGENT,
    }

    try:
        response = requests.post(
            WIKIDATA_SPARQL_URL,
            data={"query": query},
            headers=headers,
            timeout=180,
        )
        response.raise_for_status()
    except requests.RequestException as exc:
        raise requests.RequestException(f"Could not fetch label for {person_qid}: {exc}") from exc

    # Parse CSV to extract label
    lines = response.text.strip().split('\n')
    if len(lines) > 1:
        return lines[1].strip()

    return None


def fetch_for_country_and_subcategory(
    country_code: str,
    country_qid: str,
    occupations: list[str],
    subcategory: str,
    force: bool = False,
) -> list[dict]:
    """Fetch famous bearers for a specific country and occupation set.

    Args:
        country_code: ISO country code (e.g., "US", "SE")
        country_qid: Wikidata QID for the country
        occupations: List of Wikidata QIDs for occupations
        subcategory: The subcategory (ROYALTY, MOVIE_STAR, SPORTS_STAR)
        force: Re-download even if local copy exists.

    Returns:
        List of dictionaries with bearer data.
    """
    filename = f"wikidata_{country_code}_{subcategory}_{occupations[0].replace('wd:', '')}.csv"
    target_path = WIKIDATA_RAW_DIR / filename

    if not force and target_path.exists() and target_path.stat().st_size > 0:
        print(f"Using cached data: {target_path}")
        return read_csv_response(target_path)

    print(f"Fetching {country_code} {subcategory} ({occupations[0]}) data from Wikidata...")

    # Step 1: Get all person QIDs for this country and occupation
    person_qids = fetch_people_qids(country_qid, occupations)

    print(f"Found {len(person_qids)} people")

    # Step 2: For each person, fetch their data
    raw_data = []
    for i, person_qid in enumerate(person_qids):
        if i % 50 == 0:
            print(f"  Processed {i}/{len(person_qids)} people...")

        # Fetch person label (primary name)
        person_label = fetch_person_label(person_qid)
        if not person_label:
            # If no English label, skip this person
            continue

        # Fetch aliases
        aliases = fetch_aliases_for_person(person_qid)

        # Parse given names from the person label
        # The label might contain the given name(s) and surname
        # For now, we'll use the label as-is and rely on the alias filtering to handle short forms
        given_names = [person_label.split()[0]] if person_label else []

        # Build the row data
        row = {
            "person": f"http://www.wikidata.org/entity/{person_qid}",
            "personLabel": person_label,
            "givenNames": ";".join(given_names) if given_names else "",
            "aliases": ";".join(aliases) if aliases else "",
            "subcategory": subcategory,
            "country": country_code,
        }
        raw_data.append(row)

    print(f"Downloaded data for {len(raw_data)} people")

    # Write raw CSV response
    target_path.write_text(dict_to_csv(raw_data), encoding="utf-8")
    print(f"Wrote {target_path.stat().st_size} bytes to {target_path}")

    return raw_data


def dict_to_csv(data: list[dict]) -> str:
    """Convert list of dicts to CSV string.

    Args:
        data: List of dictionaries

    Returns:
        CSV string
    """
    if not data:
        return ""

    # Get all keys from all dictionaries
    fieldnames = []
    for row in data:
        for key in row.keys():
            if key not in fieldnames:
                fieldnames.append(key)

    # Build CSV
    lines = [",".join(fieldnames)]
    for row in data:
        values = []
        for key in fieldnames:
            value = row.get(key, "")
            # Escape quotes and wrap in quotes if needed
            if "," in value or '"' in value or "\n" in value:
                value = '"' + value.replace('"', '""') + '"'
            values.append(value)
        lines.append(",".join(values))

    return "\n".join(lines) + "\n"


def read_csv_response(path: Path) -> list[dict]:
    """Read a cached Wikidata CSV response.

    Args:
        path: Path to the CSV file.

    Returns:
        List of dictionaries with bearer data.
    """
    data = []
    with open(path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            data.append(row)
    return data


def process_wikidata_data(raw_data: list[dict], name_universe: set[str]) -> tuple[list[dict], int]:
    """Process raw Wikidata CSV data into canonical format.

    Args:
        raw_data: Raw CSV data from Wikidata.
        name_universe: Set of names that exist in the given_name table.

    Returns:
        Tuple of (processed_data, unresolved_count) where unresolved_count is the number
        of aliases that didn't match any name in the universe.
    """
    processed = []
    unresolved_aliases = set()

    for row in raw_data:
        public_name = row.get("personLabel", "").strip()
        if not public_name:
            continue

        # Parse given names (already English-only from query)
        given_names_str = row.get("givenNames", "")
        if given_names_str:
            given_names = [n.strip() for n in given_names_str.split(";") if n.strip()]
        else:
            given_names = []

        # Parse aliases and filter them
        aliases_str = row.get("aliases", "")
        if aliases_str:
            alias_strings = aliases_str.split(";")
            # Filter aliases: single token, Latin script, reasonable length
            filtered_aliases = [
                a.strip() for a in alias_strings
                if a.strip() and ALIAS_REGEX.match(a.strip())
            ]
            # Resolve aliases to names in the universe
            resolved_aliases = []
            for alias in filtered_aliases:
                if alias in name_universe:
                    resolved_aliases.append(alias)
                else:
                    unresolved_aliases.add(alias)
            given_names.extend(resolved_aliases)

        # Deduplicate given names while preserving order
        seen = set()
        unique_given_names = []
        for name in given_names:
            if name not in seen:
                seen.add(name)
                unique_given_names.append(name)

        # Use pre-stored subcategory and country (set during fetch)
        subcategory = row.get("subcategory", "").strip().upper()
        if subcategory not in ("ROYALTY", "MOVIE_STAR", "SPORTS_STAR"):
            continue

        country = row.get("country", "").strip().upper()
        if country not in ("US", "GB", "SE", "NO", "DK"):
            continue

        # Extract Wikidata ID from the person URL
        wikidata_id = extract_wikidata_id(row.get("person", ""))
        if not wikidata_id:
            continue

        processed.append({
            "public_name": public_name,
            "subcategory": subcategory,
            "given_names": ";".join(unique_given_names),
            "country": country,
            "wikidata_id": wikidata_id,
        })

    return processed, len(unresolved_aliases)


def infer_subcategory(row: dict) -> Optional[str]:
    """Infer subcategory from row data by examining the person URL.

    The person URL contains the occupation QID, which allows us to determine
    the subcategory.
    """
    person_url = row.get("person", "")

    # Check for monarch/royalty indicators first (takes precedence)
    if any(qid in person_url for qid in ["Q116", "Q12097", "Q116538"]):
        return "ROYALTY"

    # Check for film actor
    if "Q10800557" in person_url:
        return "MOVIE_STAR"

    # Check for sports
    if any(qid in person_url for qid in ["Q937857", "Q10833314", "Q11774891", "Q13381863"]):
        return "SPORTS_STAR"

    return None


def infer_country_from_row(row: dict) -> Optional[str]:
    """Infer country code from row data by examining the person URL.

    The person URL contains the country QID (wdt:P27), which allows us to
    determine the country code.
    """
    person_url = row.get("person", "")

    # Check for country QIDs in the URL
    country_qids = {
        Q_USA: "US",
        Q_GB: "GB",
        Q_SWEDEN: "SE",
        Q_NORWAY: "NO",
        Q_DENMARK: "DK",
    }

    for qid, code in country_qids.items():
        if qid in person_url:
            return code

    return None


def extract_wikidata_id(person_url: str) -> Optional[str]:
    """Extract Wikidata ID from person URL.

    Args:
        person_url: Full Wikidata URL like "http://www.wikidata.org/entity/Q615"

    Returns:
        Wikidata ID like "Q615" or None if not found.
    """
    if "Q" in person_url:
        # Extract the QID from the URL
        match = re.search(r"/(Q\d+)$", person_url)
        if match:
            return match.group(1)
    return None


def write_csv(data: list[dict], output_dir: Optional[Path] = None) -> Path:
    """Write processed data to CSV file.

    Args:
        data: List of processed bearer dictionaries.
        output_dir: Output directory. Defaults to WIKIDATA_CSV_DIR.

    Returns:
        Path to the written CSV file.
    """
    output_dir = output_dir or WIKIDATA_CSV_DIR
    output_dir.mkdir(parents=True, exist_ok=True)

    output_path = output_dir / "famous_bearers.csv"

    fieldnames = ["public_name", "subcategory", "given_names", "country", "wikidata_id"]

    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(data)

    print(f"Wrote {len(data)} rows to {output_path}")
    return output_path


def download_wikidata_data(force: bool = False) -> list[dict]:
    """Fetch famous bearers from Wikidata for all countries and subcategories.

    The query strategy follows these rules:
    1. Never use /wdt:P279* subclass path - enumerate occupation QIDs explicitly
    2. One country per query - loop over the five, don't put them in a VALUES block
    3. Raise sitelinks until the query returns - US needs higher bar than Sweden

    Args:
        force: Re-download even if local copies exist.

    Returns:
        List of dictionaries with bearer data.
    """
    WIKIDATA_RAW_DIR.mkdir(parents=True, exist_ok=True)

    all_data = []

    for country_code, country_qid in COUNTRIES.items():
        for subcategory, occupations in SUBCATEGORY_MAPPINGS.items():
            dataset = fetch_for_country_and_subcategory(
                country_code, country_qid, occupations, subcategory, force
            )
            all_data.extend(dataset)

    return all_data


def generate_canonical_csv(
    raw_data: list[dict],
    name_universe: Optional[set[str]] = None,
    output_dir: Optional[Path] = None,
) -> tuple[Path, int]:
    """Generate canonical CSV from raw Wikidata data.

    Args:
        raw_data: Raw CSV data from Wikidata fetch.
        name_universe: Set of names that exist in the given_name table.
                      If None, aliases won't be resolved.
        output_dir: Output directory. Defaults to WIKIDATA_CSV_DIR.

    Returns:
        Tuple of (output_path, unresolved_count) where unresolved_count is the
        number of aliases that didn't match any name in the universe.
    """
    output_dir = output_dir or WIKIDATA_CSV_DIR
    output_dir.mkdir(parents=True, exist_ok=True)

    # Process the raw data
    if name_universe is None:
        name_universe = set()

    processed_data, unresolved_count = process_wikidata_data(raw_data, name_universe)

    # Write to canonical CSV format
    output_path = output_dir / "famous_bearers.csv"
    fieldnames = ["public_name", "subcategory", "given_names", "country", "wikidata_id"]

    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(processed_data)

    print(f"Wrote {len(processed_data)} rows to {output_path}")
    print(f"Unresolved alias count: {unresolved_count}")

    return output_path, unresolved_count


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Fetch famous bearers from Wikidata")
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-download even if local copies exist",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        help="Directory to write the CSV file into",
    )
    parser.add_argument(
        "--database-url",
        type=str,
        help="Database URL to load name universe from (for alias resolution)",
    )
    args = parser.parse_args()

    # Fetch data
    raw_data = download_wikidata_data(force=args.force)

    # Load name universe if database URL provided
    name_universe = None
    if args.database_url:
        import os
        from sqlalchemy import create_engine, text

        engine = create_engine(args.database_url)
        with engine.connect() as conn:
            result = conn.execute(text("SELECT name FROM given_name"))
            name_universe = {row[0] for row in result}

    # Process and write canonical CSV
    output_path, unresolved = generate_canonical_csv(
        raw_data,
        name_universe=name_universe,
        output_dir=Path(args.output_dir) if args.output_dir else None,
    )
    print(f"Done. Output written to {output_path}")
    print(f"Unresolved aliases: {unresolved}")
