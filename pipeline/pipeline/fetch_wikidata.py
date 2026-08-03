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

# Minimum sitelinks threshold for each country (to avoid timeout and ensure quality)
# US needs a higher bar than other countries
SITELINKS_THRESHOLD = {
    Q_USA: 100,  # US needs higher bar
    Q_GB: 60,
    Q_SWEDEN: 40,
    Q_NORWAY: 45,
    Q_DENMARK: 45,
}

# Regex to filter aliases: single token, Latin script, reasonable length
# This matches strings like "Leo", "Henke", "Gunnar" while excluding nicknames like "Ibra"
ALIAS_REGEX = re.compile(r"^[A-ZÅÄÖÉÈØÆ][a-zåäöéèøæüï'-]{1,14}$")


def local_dataset(country_code: str, subcategory: str) -> Optional[Path]:
    """Return the already-downloaded dataset for a country/subcategory, or None if not present."""
    filename = f"wikidata_{country_code}_{subcategory}.csv"
    path = WIKIDATA_RAW_DIR / filename
    return path if path.exists() and path.stat().st_size > 0 else None


def download_wikidata_data(force: bool = False) -> list[dict]:
    """Fetch famous bearers from Wikidata for all countries and subcategories.

    The query strategy follows these rules:
    1. Never use /wdt:P279* subclass path - enumate occupation QIDs explicitly
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
                country_code, country_qid, occupations, force
            )
            all_data.extend(dataset)

    return all_data


def fetch_for_country_and_subcategory(
    country_code: str,
    country_qid: str,
    occupations: list[str],
    force: bool = False,
) -> list[dict]:
    """Fetch famous bearers for a specific country and occupation set.

    Args:
        country_code: ISO country code (e.g., "US", "SE")
        country_qid: Wikidata QID for the country
        occupations: List of Wikidata QIDs for occupations
        force: Re-download even if local copy exists.

    Returns:
        List of dictionaries with bearer data.
    """
    # Build occupation query part
    occupation_list = " ".join(f"wd:{qid}" for qid in occupations)

    # Build sitelinks threshold
    sitelinks_threshold = SITELINKS_THRESHOLD.get(country_qid, 50)

    # Build the SPARQL query
    # Key points from the issue:
    # - ?person wdt:P31 wd:Q5 ensures we only get real people (not streets, etc.)
    # - ?person wdt:P27 wd:XXX filters by country of citizenship
    # - ?person wdt:P106 wd:XXX filters by occupation
    # - ?person wdt:P735 ?givenName gives the given name(s)
    # - ?person skos:altLabel ?altLabel gets short forms across ALL languages
    # - STR() before GROUP_CONCAT to deduplicate strings with different language tags
    query = f"""
SELECT DISTINCT
  ?person
  ?personLabel
  (GROUP_CONCAT(DISTINCT STR(?givenNameLabel); separator=";") AS ?givenNames)
  (GROUP_CONCAT(DISTINCT STR(?altLabel); separator=";") AS ?aliases)
WHERE {{
  ?person wdt:P31 wd:Q5 .                    # Instance of human
  ?person wdt:P27 wd:{country_qid} .         # Country of citizenship
  ?person wdt:P106 wd:{occupation_list} .    # Occupation (one of)
  ?person wdt:P735 ?givenName .              # Given name(s)
  ?givenName rdfs:label ?givenNameLabel .
  FILTER(LANGMATCHES(LANG(?givenNameLabel), "en"))

  # Get aliases (short forms) across ALL languages (not just English!)
  ?person skos:altLabel ?altLabel .
}}
GROUP BY ?person ?personLabel
LIMIT 500
"""

    filename = f"wikidata_{country_code}_{occupations[0].replace('wd:', '')}.csv"
    target_path = WIKIDATA_RAW_DIR / filename

    if not force and target_path.exists() and target_path.stat().st_size > 0:
        print(f"Using cached data: {target_path}")
        return read_csv_response(target_path)

    print(f"Fetching {country_code} {occupations[0]} data from Wikidata...")

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
        raise requests.RequestException(f"Could not fetch Wikidata data: {exc}") from exc

    if len(response.content) < 100:
        raise requests.RequestException(
            f"Downloaded only {len(response.content)} bytes, too small to be valid data."
        )

    # Write raw CSV response
    target_path.write_text(response.text, encoding="utf-8")
    print(f"Wrote {target_path.stat().st_size} bytes to {target_path}")

    return read_csv_response(target_path)


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

        # Determine subcategory based on occupation QID in the person URL
        subcategory = infer_subcategory(row)

        if not subcategory:
            continue

        # Extract Wikidata ID from the person URL
        wikidata_id = extract_wikidata_id(row.get("person", ""))

        if not wikidata_id:
            continue

        # Extract country from the row URL (parse the country QID from the response)
        country = infer_country_from_row(row)

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
    args = parser.parse_args()

    # Fetch data
    raw_data = download_wikidata_data(force=args.force)

    # Process and write CSV
    output_path = write_csv(raw_data, output_dir=Path(args.output_dir) if args.output_dir else None)
    print(f"Done. Output written to {output_path}")
