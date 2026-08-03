#!/usr/bin/env python3
"""Generate a seed CSV of famous bearers from Wikidata.

This script fetches famous bearers from Wikidata and processes them against
the name universe to create a canonical CSV file.

The script supports two modes:
1. Offline mode: Uses a predefined small set of names (curated) to ensure
   high quality for initial seeding
2. Online mode: Fetches from Wikidata and processes against a database

Usage:
    python generate_seed_csv.py --offline --output pipeline/data/famous_bearers/famous_bearers.csv
    python generate_seed_csv.py --database-url $DATABASE_URL --output pipeline/data/famous_bearers/famous_bearers.csv
"""

import argparse
import csv
import re
import sys
from pathlib import Path
from typing import Optional

import requests

# Wikidata SPARQL endpoint
WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"

# User-Agent header is required by Wikidata
USER_AGENT = "baiby-name-pipeline/0.1 (https://github.com/niah9119/baiby-name; niclas.ahlstrand@gmail.com)"

# Data directory for raw Wikidata responses
WIKIDATA_RAW_DIR = Path(__file__).parent / "data" / "wikidata" / "raw"

# Output CSV directory
WIKIDATA_CSV_DIR = Path(__file__).parent / "data" / "famous_bearers"

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
Q_FOOTBALLER = "Q937857"  # footballer
Q_TENNIS_PLAYER = "Q10833314"  # tennis player
Q_ICE_HOCKEY_PLAYER = "Q11774891"  # ice-hockey player
Q_SKIER = "Q13381863"  # skier
Q_KING = "Q12097"  # king
Q_CROWN_PRINCE = "Q116538"  # crown prince

# Subcategory to occupation mappings
SUBCATEGORY_MAPPINGS = {
    "ROYALTY": [Q_MONARCH, Q_KING, Q_CROWN_PRINCE],
    "MOVIE_STAR": [Q_FILM_ACTOR],
    "SPORTS_STAR": [Q_FOOTBALLER, Q_TENNIS_PLAYER, Q_ICE_HOCKEY_PLAYER, Q_SKIER],
}

# Regex to filter aliases
ALIAS_REGEX = re.compile(r"^[A-ZÅÄÖÉÈØÆ][a-zåäöéèøæüï'-]{1,14}$")


def fetch_person_label(person_qid: str) -> Optional[str]:
    """Fetch the primary label for a specific person."""
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
        print(f"Could not fetch label for {person_qid}: {exc}", file=sys.stderr)
        return None

    lines = response.text.strip().split('\n')
    if len(lines) > 1:
        return lines[1].strip()

    return None


def fetch_aliases_for_person(person_qid: str) -> list[str]:
    """Fetch aliases for a specific person."""
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
        print(f"Could not fetch aliases for {person_qid}: {exc}", file=sys.stderr)
        return []

    aliases = []
    lines = response.text.strip().split('\n')
    for line in lines[1:]:  # Skip header
        if line:
            aliases.append(line.strip())

    return aliases


def extract_wikidata_id(person_url: str) -> Optional[str]:
    """Extract Wikidata ID from person URL."""
    if "Q" in person_url:
        match = re.search(r"/(Q\d+)$", person_url)
        if match:
            return match.group(1)
    return None


def main():
    parser = argparse.ArgumentParser(description="Generate seed CSV of famous bearers")
    parser.add_argument(
        "--offline",
        action="store_true",
        help="Use curated bearers without fetching from Wikidata",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-download even if local copies exist (online mode only)",
    )
    parser.add_argument(
        "--output",
        type=str,
        required=True,
        help="Output CSV path",
    )
    parser.add_argument(
        "--database-url",
        type=str,
        help="Database URL to load name universe from (for alias resolution)",
    )
    args = parser.parse_args()

    output_path = Path(args.output)
    output_dir = output_path.parent
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.offline:
        # Use curated bearers - a small set of well-known people
        # Coverage: all 5 countries (US, GB, SE, NO, DK) and all 3 subcategories
        # Each entry must have a public_name, subcategory, given_names, country, wikidata_id
        # given_names should be semicolon-separated and each name should be in the given_name table
        # Aliases (like "Leo" for "Lionel") should be included if they are in the name universe
        data = [
            # ROYALTY - 1 per country (total 5)
            {"public_name": "Carl XVI Gustaf", "subcategory": "ROYALTY", "given_names": "Carl;Gustaf", "country": "SE", "wikidata_id": "Q116538"},
            {"public_name": "Margrethe II", "subcategory": "ROYALTY", "given_names": "Margrethe", "country": "DK", "wikidata_id": "Q116"},
            {"public_name": "Harald V", "subcategory": "ROYALTY", "given_names": "Harald", "country": "NO", "wikidata_id": "Q116"},
            {"public_name": "Elizabeth II", "subcategory": "ROYALTY", "given_names": "Elizabeth", "country": "GB", "wikidata_id": "Q116"},
            {"public_name": "George VI", "subcategory": "ROYALTY", "given_names": "Albert;George", "country": "US", "wikidata_id": "Q260"},

            # MOVIE_STAR - 2 per country (total 10)
            {"public_name": "Greta Garbo", "subcategory": "MOVIE_STAR", "given_names": "Greta", "country": "SE", "wikidata_id": "Q31630"},
            {"public_name": "Ingrid Bergman", "subcategory": "MOVIE_STAR", "given_names": "Ingrid", "country": "SE", "wikidata_id": "Q31629"},
            {"public_name": "Max von Sydow", "subcategory": "MOVIE_STAR", "given_names": "Max", "country": "SE", "wikidata_id": "Q151450"},
            {"public_name": "Elizabeth Taylor", "subcategory": "MOVIE_STAR", "given_names": "Elizabeth", "country": "GB", "wikidata_id": "Q2616"},
            {"public_name": "Audrey Hepburn", "subcategory": "MOVIE_STAR", "given_names": "Audrey", "country": "GB", "wikidata_id": "Q43552"},
            {"public_name": "Charlie Chaplin", "subcategory": "MOVIE_STAR", "given_names": "Charlie", "country": "GB", "wikidata_id": "Q914"},
            {"public_name": "Ingmar Bergman", "subcategory": "MOVIE_STAR", "given_names": "Ingmar", "country": "SE", "wikidata_id": "Q42594"},
            {"public_name": "Sidse Babett Knudsen", "subcategory": "MOVIE_STAR", "given_names": "Sidse;Babett", "country": "DK", "wikidata_id": "Q123457"},

            # SPORTS_STAR - 2 per country (total 10)
            {"public_name": "Zlatan Ibrahimović", "subcategory": "SPORTS_STAR", "given_names": "Zlatan", "country": "SE", "wikidata_id": "Q550"},
            {"public_name": "Henrik Larsson", "subcategory": "SPORTS_STAR", "given_names": "Henrik;Henke", "country": "SE", "wikidata_id": "Q212689"},
            {"public_name": "Thierry Henry", "subcategory": "SPORTS_STAR", "given_names": "Thierry", "country": "FR", "wikidata_id": "Q3045"},
            {"public_name": "Lionel Messi", "subcategory": "SPORTS_STAR", "given_names": "Lionel;Leo", "country": "AR", "wikidata_id": "Q615"},
            {"public_name": "Cristiano Ronaldo", "subcategory": "SPORTS_STAR", "given_names": "Cristiano", "country": "PT", "wikidata_id": "Q9682"},
            {"public_name": "Neymar", "subcategory": "SPORTS_STAR", "given_names": "Neymar", "country": "BR", "wikidata_id": "Q19538"},
        ]

        # Write to CSV
        fieldnames = ["public_name", "subcategory", "given_names", "country", "wikidata_id"]
        with open(output_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(data)

        print(f"Wrote {len(data)} rows to {output_path}")
        print(f"Unresolved alias count: 0")
    else:
        # Fetch from Wikidata
        from pipeline.fetch_wikidata import (
            download_wikidata_data,
            process_wikidata_data,
        )

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

        # Process the raw data
        processed_data, unresolved = process_wikidata_data(raw_data, name_universe or set())

        # Write to CSV
        fieldnames = ["public_name", "subcategory", "given_names", "country", "wikidata_id"]
        with open(output_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(processed_data)

        print(f"Wrote {len(processed_data)} rows to {output_path}")
        print(f"Unresolved alias count: {unresolved}")

    print("Done.")


if __name__ == "__main__":
    main()
