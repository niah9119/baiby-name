"""Style classification pipeline for given names.

This module provides offline classification of style attributes:
- style_score: traditional (-100) to modern (+100)
- syllable_count: number of syllables in the name
- sound_character: soft (-100) to strong (+100)
- origin: cultural origin of the name
- international: whether the name works across many languages

The classification happens in two phases:
1. Algorithmic classification (syllable count, traditional/modern scoring)
2. LLM batch classification (origin, sound character, international)

Nothing is written to the database until a human reviews the CSV outputs.
"""

import csv
import os
import re
from pathlib import Path
from typing import Optional

import pandas as pd


# Path for output CSVs
STYLE_CLASSIFICATION_DIR = Path(__file__).resolve().parent.parent / "data" / "style_classification"
STYLE_CLASSIFICATION_DIR.mkdir(parents=True, exist_ok=True)

# Default LLM endpoint configuration
LLM_ENDPOINT = os.environ.get("LLM_ENDPOINT", "http://localhost:8002/v1/chat/completions")
LLM_API_KEY = os.environ.get("LLM_API_KEY", "dummy")

# Review CSV paths
SYLLABLE_CSV_PATH = STYLE_CLASSIFICATION_DIR / "syllable_classification.csv"
TRADITIONAL_CSV_PATH = STYLE_CLASSIFICATION_DIR / "traditional_modern_classification.csv"
ORIGIN_CSV_PATH = STYLE_CLASSIFICATION_DIR / "origin_classification.csv"
SOUND_CHARACTER_CSV_PATH = STYLE_CLASSIFICATION_DIR / "sound_character_classification.csv"
INTERNATIONAL_CSV_PATH = STYLE_CLASSIFICATION_DIR / "international_classification.csv"


def count_syllables(word: str) -> int:
    """
    Estimate the number of syllables in a name using heuristic rules.

    This uses a simple vowel-counting algorithm that works reasonably well
    for international names.

    Args:
        word: The name to count syllables for

    Returns:
        Estimated syllable count (1 or more)
    """
    word = word.lower().strip()

    if not word:
        return 0

    vowels = "aeiouy"
    count = 0
    prev_was_vowel = False

    for char in word:
        is_vowel = char in vowels
        if is_vowel and not prev_was_vowel:
            count += 1
        prev_was_vowel = is_vowel

    # Handle silent e at the end
    if word.endswith("e") and count > 1:
        count -= 1

    # Handle common suffixes that add syllables
    suffixes = {
        "ia": 1, "ee": 1, "ie": 1, "y": 0,  # y at end usually doesn't add
        "tion": 2, "sion": 2, "ian": 2, "ior": 2,
    }
    for suffix, add in suffixes.items():
        if word.endswith(suffix):
            count += add

    # Handle special cases
    special_cases = {
        "eau": 1,  # French "eau" is one syllable
        "io": 2,   # "io" is usually two syllables
        "ua": 2,   # "ua" is usually two syllables
    }
    for pattern, add in special_cases.items():
        if pattern in word:
            count += add

    # Ensure at least one syllable
    return max(1, count)


def estimate_traditional_score(name: str, country_code: str) -> int:
    """
    Estimate how traditional vs modern a name is based on historical data patterns.

    This uses heuristic rules based on:
    - Name length (longer names tend to be more modern)
    - Name endings (traditional endings like -us, -a, -is vs modern -er, -on, -yn)
    - Common traditional names from the country's history

    Args:
        name: The name to score
        country_code: ISO country code (US, SE, NO, DK, GB)

    Returns:
        Score from -100 (very traditional) to +100 (very modern)
    """
    name_lower = name.lower()
    name_len = len(name)

    # Start with a neutral score
    score = 0

    # Length factor: longer names tend to be more modern
    if name_len >= 8:
        score += 30
    elif name_len >= 6:
        score += 15
    elif name_len <= 4:
        score -= 10

    # Ending patterns
    traditional_endings = {
        "US": ["us", "is", "as", "os", "a", "ia", "ia", "us"],
        "SE": ["us", "is", "as", "a", "ar", "er", "ir"],
        "NO": ["us", "is", "as", "a", "ar", "er", "ir", "vin"],
        "DK": ["us", "is", "as", "a", "ar", "er", "ild", "hart"],
        "GB": ["us", "is", "as", "a", "ia", "ius", "ex", "ian"],
    }

    modern_endings = {
        "US": ["er", "on", "yn", "in", "yn", "ez", "on"],
        "SE": ["er", "on", "in", "yn", "son"],
        "NO": ["er", "on", "in", "yn", "son", "sen"],
        "DK": ["er", "on", "in", "yn", "sen", "gaard"],
        "GB": ["er", "on", "in", "yn", "son", "son"],
    }

    for ending in traditional_endings.get(country_code, traditional_endings["US"]):
        if name_lower.endswith(ending):
            score -= 20
            break

    for ending in modern_endings.get(country_code, modern_endings["US"]):
        if name_lower.endswith(ending):
            score += 20
            break

    # Common traditional names (historical, royal, biblical)
    traditional_names = {
        "US": ["John", "Mary", "Robert", "Elizabeth", "William", "Margaret",
               "Michael", "Sarah", "David", "Hannah", "Joseph", "Rachel"],
        "SE": ["Gustav", "Erik", "Svante", "Birger", "Dagny", "Freja",
               "Olof", "Astrid", "Ivan", "Signe"],
        "NO": ["Harald", "Olav", "Magnus", "Aud", "Ragnhild", "Sigurd",
               "Ingrid", "Einar", "Kari", "Tor"],
        "DK": ["Christian", "Frederik", "Margrethe", "Knud", "Valdemar",
               "Inge", "Lars", "Mette", "Jens"],
        "GB": ["William", "Henry", "Edward", "Richard", "Elizabeth", "Margaret",
               "Thomas", "Catherine", "George", "Mary"],
    }

    modern_names = {
        "US": ["Liam", "Olivia", "Noah", "Emma", "Oliver", "Charlotte",
               "Leo", "Mia", "Luca", "Ava"],
        "SE": ["Noah", "Alice", "Lucas", "Maja", "Elias", "Agnes",
               "William", "Stella", "Leo", "Freja"],
        "NO": ["Sander", "Olav", "Aksel", "Nora", "Emma", "Mia",
               "Sofie", "Vetle", "Sunniva", "Marte"],
        "DK": ["Aubrey", "Freja", "Ebbe", "Ivy", "Arne", "Eva",
               "Magnus", "Frederikke", "Alf", "Luna"],
        "GB": ["Muhammad", "Harry", "Oliver", "George", "Leo", "Noah",
               "Arthur", "Oscar", "Henry", "Frederick"],
    }

    # Lowercase check for names
    if name_lower in [n.lower() for n in traditional_names.get(country_code, traditional_names["US"])]:
        score -= 50
    elif name_lower in [n.lower() for n in modern_names.get(country_code, modern_names["US"])]:
        score += 50

    # Clamp to valid range
    return max(-100, min(100, score))


def classify_sound_character(name: str) -> int:
    """
    Classify the sound character of a name as soft (-100) or strong (+100).

    This uses phonetic heuristics based on:
    - Vowel consonant ratio (more vowels = softer)
    - Specific soft sounds (l, m, n, r, w) vs strong sounds (k, t, p, b, d, g)
    - Name endings

    Args:
        name: The name to classify

    Returns:
        Score from -100 (very soft) to +100 (very strong)
    """
    name_lower = name.lower()
    vowels = set("aeiouy")
    consonants = set("bcdfghjklmnpqrstvwxyz")

    soft_consonants = set("lmnrw")
    strong_consonants = set("ktpbdg")

    soft_vowels = set("eiy")
    hard_vowels = set("aou")

    soft_score = 0
    strong_score = 0

    # Count soft vs strong sounds
    for char in name_lower:
        if char in vowels:
            # "y" at end is counted as soft consonant, not vowel
            if char == "y" and name_lower.endswith("y"):
                soft_score += 1  # counted as soft consonant instead of vowel
            elif char in soft_vowels:
                soft_score += 1
            else:
                strong_score += 1
        elif char in consonants:
            if char in soft_consonants:
                soft_score += 1
            elif char in strong_consonants:
                strong_score += 2

    # Ending analysis - "y" ending already counted above
    soft_endings = ["a", "e", "ia", "ie", "ya", "ye"]
    strong_endings = ["k", "t", "d", "g", "z", "ck", "tt", "pp"]

    for ending in soft_endings:
        if name_lower.endswith(ending):
            soft_score += 1

    for ending in strong_endings:
        if name_lower.endswith(ending):
            strong_score += 2

    # Calculate final score
    total = soft_score + strong_score
    if total == 0:
        return 0

    # Normalize to -100 to +100 range, but with a gentler curve
    # Use a sigmoid-like mapping to keep values more moderate
    raw_ratio = (soft_score - strong_score) / max(1, total)

    # Apply a penalty for names with no strong elements to avoid extreme soft scores
    if strong_score == 0:
        # Names with only soft elements get a moderate score (around 30-40)
        # This prevents names like "Lily" from being too soft
        return int(soft_score * 5)
    else:
        # Map from [-1, 1] to [-70, 70] to avoid extreme values
        return int(raw_ratio * 70)


def generate_syllable_csv(
    canonical_csv_path: Optional[Path] = None,
    output_path: Optional[Path] = None
) -> Path:
    """
    Generate a CSV file with syllable classifications for all names.

    Args:
        canonical_csv_path: Path to the canonical CSV with names
        output_path: Path to write the classification CSV

    Returns:
        Path to the generated CSV file
    """
    from .config import CANONICAL_CSV_PATH

    canonical_csv_path = canonical_csv_path or CANONICAL_CSV_PATH
    output_path = output_path or SYLLABLE_CSV_PATH

    if not canonical_csv_path.exists():
        raise FileNotFoundError(f"Canonical CSV not found: {canonical_csv_path}")

    # Read the canonical CSV and get unique names
    df = pd.read_csv(canonical_csv_path)
    unique_names = df["name"].unique()

    # Classify each name
    results = []
    for name in unique_names:
        syllables = count_syllables(name)
        results.append({
            "name": name,
            "syllable_count": syllables,
            "classification_method": "algorithmic",
            "notes": "",
        })

    # Write to CSV
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["name", "syllable_count", "classification_method", "notes"])
        writer.writeheader()
        writer.writerows(results)

    print(f"Syllable classification written to: {output_path}")
    print(f"Total names classified: {len(results)}")

    return output_path


def generate_traditional_csv(
    canonical_csv_path: Optional[Path] = None,
    output_path: Optional[Path] = None
) -> Path:
    """
    Generate a CSV file with traditional/modern scores for all names.

    Args:
        canonical_csv_path: Path to the canonical CSV with names
        output_path: Path to write the classification CSV

    Returns:
        Path to the generated CSV file
    """
    from .config import CANONICAL_CSV_PATH, USA_COUNTRY_CODE

    canonical_csv_path = canonical_csv_path or CANONICAL_CSV_PATH
    output_path = output_path or TRADITIONAL_CSV_PATH

    if not canonical_csv_path.exists():
        raise FileNotFoundError(f"Canonical CSV not found: {canonical_csv_path}")

    # Read the canonical CSV and get unique names
    df = pd.read_csv(canonical_csv_path)

    # Classify each row from the canonical CSV
    results = []
    for _, row in df.iterrows():
        score = estimate_traditional_score(row["name"], row["country"])
        results.append({
            "name": row["name"],
            "country": row["country"],
            "style_score": score,
            "classification_method": "algorithmic",
            "notes": "",
        })

    # Write to CSV
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["name", "country", "style_score", "classification_method", "notes"])
        writer.writeheader()
        writer.writerows(results)

    print(f"Traditional/modern classification written to: {output_path}")
    print(f"Total classifications: {len(results)}")

    return output_path


def generate_llm_classification_csv(
    canonical_csv_path: Optional[Path] = None,
    classification_type: str = "origin",
    output_path: Optional[Path] = None
) -> Path:
    """
    Generate a CSV file for LLM classification (origin, sound_character, international).

    This creates a prompt file that can be batch-processed by an LLM.

    Args:
        canonical_csv_path: Path to the canonical CSV with names
        classification_type: Type of classification (origin, sound_character, international)
        output_path: Path to write the classification CSV

    Returns:
        Path to the generated CSV file
    """
    from .config import CANONICAL_CSV_PATH

    canonical_csv_path = canonical_csv_path or CANONICAL_CSV_PATH
    output_path = output_path or {
        "origin": ORIGIN_CSV_PATH,
        "sound_character": SOUND_CHARACTER_CSV_PATH,
        "international": INTERNATIONAL_CSV_PATH,
    }.get(classification_type)

    if not canonical_csv_path.exists():
        raise FileNotFoundError(f"Canonical CSV not found: {canonical_csv_path}")

    # Read the canonical CSV and get unique names
    df = pd.read_csv(canonical_csv_path)
    unique_names = df["name"].unique()

    # Get unique countries for origin classification
    countries = df["country"].unique().tolist()

    # Determine the output columns based on classification type
    # Each type gets a specific column that will be filled by the LLM
    base_columns = ["name", "prompt", "llm_response", "classification_method", "notes"]
    output_columns = base_columns.copy()

    if classification_type == "origin":
        # Insert origin column after name
        output_columns.insert(1, "origin")
    elif classification_type == "sound_character":
        # Insert sound_character column after name
        output_columns.insert(1, "sound_character")
    elif classification_type == "international":
        # Insert international column after name
        output_columns.insert(1, "international")

    # Classify each name
    results = []
    for name in unique_names:
        # Build the prompt based on classification type
        if classification_type == "origin":
            countries_list = ", ".join(countries)
            prompt = (
                f"Name: {name}\n"
                f"Available countries: {countries_list}\n"
                f"Question: What is the cultural origin of this name? "
                f"Consider the name's linguistic roots and cultural associations. "
                f"Answer with a single origin category such as: English, Scandinavian, Germanic, "
                f"Latin, Greek, Hebrew, Arabic, Asian, African, or Other."
            )
        elif classification_type == "sound_character":
            prompt = (
                f"Name: {name}\n"
                f"Question: How would you describe the sound character of this name? "
                f"Is it soft (gentle, flowing sounds) or strong (strong consonants, punchy sounds)? "
                f"Rate on a scale from -100 (very soft) to +100 (very strong). "
                f"Return only a number."
            )
        elif classification_type == "international":
            prompt = (
                f"Name: {name}\n"
                f"Question: Does this name work well across many languages and cultures, "
                f"or is it strongly tied to one culture? "
                f"Answer with 'true' if it's international, 'false' if it's culture-specific."
            )
        else:
            raise ValueError(f"Unknown classification type: {classification_type}")

        # Build the result row with the classification-specific column
        result = {
            "name": name,
            "prompt": prompt,
            "llm_response": "",
            "classification_method": "llm_batch",
            "notes": "",
        }

        # Add the classification-specific column (empty initially)
        if classification_type == "origin":
            result["origin"] = ""
        elif classification_type == "sound_character":
            result["sound_character"] = ""
        elif classification_type == "international":
            result["international"] = ""

        results.append(result)

    # Write to CSV
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=output_columns)
        writer.writeheader()
        writer.writerows(results)

    print(f"LLM classification CSV written to: {output_path}")
    print(f"Total prompts generated: {len(results)}")

    return output_path


def process_llm_responses(
    input_csv_path: Path,
    output_csv_path: Optional[Path] = None
) -> Path:
    """
    Process LLM responses from a classification CSV and update the results.

    This reads a CSV with prompts and LLM responses, validates the responses,
    and creates a final classification output.

    Args:
        input_csv_path: Path to the CSV with prompts and empty/LLM responses
        output_csv_path: Path to write the processed results

    Returns:
        Path to the processed CSV file
    """
    output_path = output_csv_path or input_csv_path

    if not input_csv_path.exists():
        raise FileNotFoundError(f"Input CSV not found: {input_csv_path}")

    # Determine classification type from filename
    input_str = str(input_csv_path)
    if "origin" in input_str:
        classification_type = "origin"
    elif "sound_character" in input_str:
        classification_type = "sound_character"
    elif "international" in input_str:
        classification_type = "international"
    else:
        classification_type = "unknown"

    # Read the CSV
    df = pd.read_csv(input_csv_path)

    # Process each row
    results = []
    for _, row in df.iterrows():
        name = row["name"]
        llm_response = row.get("llm_response", "")

        # Determine the result based on classification type
        result = {
            "name": name,
            "classification_method": "llm_processed",
            "notes": "",
        }

        # Try to parse the LLM response
        if classification_type == "origin":
            # Origin classification - just store the response as-is
            result["origin"] = llm_response if llm_response else ""
        elif classification_type == "sound_character":
            # Sound character - try to parse as integer
            try:
                score = int(str(llm_response).strip())
                result["sound_character"] = max(-100, min(100, score))
            except (ValueError, AttributeError):
                result["sound_character"] = None
        elif classification_type == "international":
            # International - parse boolean
            if llm_response is not None and str(llm_response).strip() != "":
                if str(llm_response).strip().lower() in ["true", "yes", "1"]:
                    result["international"] = True
                elif str(llm_response).strip().lower() in ["false", "no", "0"]:
                    result["international"] = False
                else:
                    result["international"] = None
            else:
                result["international"] = None

        results.append(result)

    # Write to output CSV
    output_path.parent.mkdir(parents=True, exist_ok=True)

    # Determine output columns based on classification type
    output_columns = ["name", "classification_method", "notes"]
    if classification_type == "origin":
        output_columns = ["name", "origin", "classification_method", "notes"]
    elif classification_type == "sound_character":
        output_columns = ["name", "sound_character", "classification_method", "notes"]
    elif classification_type == "international":
        output_columns = ["name", "international", "classification_method", "notes"]

    # Build final results ensuring all output columns are present
    final_results = []
    for r in results:
        final_row = {}
        for col in output_columns:
            final_row[col] = r.get(col, "")
        final_results.append(final_row)

    with open(output_path, "w", newline="", encoding="utf-8") as f:
        if final_results:
            writer = csv.DictWriter(f, fieldnames=output_columns)
            writer.writeheader()
            writer.writerows(final_results)
        else:
            # Write empty file with headers
            with open(output_path, "w") as f:
                f.write(",".join(output_columns) + "\n")

    print(f"Processed LLM responses written to: {output_path}")
    print(f"Total classifications: {len(results)}")

    return output_path


def load_style_data_from_csv(
    csv_path: Path,
    country_code: Optional[str] = None,
    db_url: Optional[str] = None
) -> int:
    """
    Load style data from a CSV into the database.

    This is used after human review of the classification CSVs.

    Args:
        csv_path: Path to the style classification CSV
        country_code: Optional country filter
        db_url: Database URL (uses DATABASE_URL if not provided)

    Returns:
        Number of records loaded
    """
    import sqlalchemy
    from sqlalchemy import text

    from .config import get_database_url

    db_url = db_url or get_database_url()
    engine = sqlalchemy.create_engine(db_url)

    if not csv_path.exists():
        raise FileNotFoundError(f"CSV file not found: {csv_path}")

    df = pd.read_csv(csv_path)

    # Filter by country if specified
    if country_code and "country" in df.columns:
        df = df[df["country"] == country_code]

    # Map column names to database fields
    column_mapping = {
        "syllable_count": "syllable_count",
        "style_score": "style_score",
        "sound_character": "sound_character",
        "origin": "origin",
        "international": "international",
    }

    loaded_count = 0

    with engine.begin() as conn:
        # Get all names and their IDs
        result = conn.execute(text("SELECT id, name FROM given_name"))
        name_cache = {row[1]: row[0] for row in result}

        # Get all countries and their IDs
        result = conn.execute(text("SELECT id, code FROM country"))
        country_cache = {row[1]: row[0] for row in result}

        for _, row in df.iterrows():
            name = row["name"]
            name_id = name_cache.get(name)

            if name_id is None:
                continue  # Skip names not in database

            # Build the update query
            updates = []
            params = {"given_name_id": name_id}

            if "country" in row.columns:
                country_code_row = row["country"]
                country_id = country_cache.get(country_code_row)
                if country_id is None:
                    continue  # Skip if country not in database
                params["country_id"] = country_id
                updates.append("country_id = :country_id")

            if "syllable_count" in row.columns:
                updates.append("syllable_count = :syllable_count")
                params["syllable_count"] = int(row["syllable_count"])

            if "style_score" in row.columns:
                updates.append("style_score = :style_score")
                params["style_score"] = int(row["style_score"])

            if "sound_character" in row.columns:
                updates.append("sound_character = :sound_character")
                params["sound_character"] = int(row["sound_character"])

            if "origin" in row.columns:
                updates.append("origin = :origin")
                params["origin"] = row["origin"]

            if "international" in row.columns:
                updates.append("international = :international")
                international_value = row["international"]
                if isinstance(international_value, str):
                    params["international"] = international_value.lower() in ["true", "yes", "1"]
                else:
                    params["international"] = bool(international_value)

            if updates:
                query = f"""
                    INSERT INTO name_style (given_name_id, {', '.join(updates)})
                    VALUES (:given_name_id, {', '.join([u.split('=')[0].strip() for u in updates])})
                    ON CONFLICT (given_name_id) DO UPDATE SET {', '.join(updates)}
                """
                # Rebuild query with proper parameters
                query = f"""
                    INSERT INTO name_style (given_name_id, {', '.join([u.split('=')[0].strip() for u in updates])})
                    VALUES (:given_name_id, {', '.join([':' + u.split('=')[0].strip() for u in updates])})
                    ON CONFLICT (given_name_id) DO UPDATE SET {', '.join(updates)}
                """
                conn.execute(text(query), params)
                loaded_count += 1

    return loaded_count


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Style classification pipeline")
    parser.add_argument("--type", choices=["syllable", "traditional", "origin", "sound_character", "international"],
                        required=True, help="Type of classification to generate")
    parser.add_argument("--input", type=str, help="Input canonical CSV path")
    parser.add_argument("--output", type=str, help="Output CSV path")

    args = parser.parse_args()

    if args.type == "syllable":
        output = generate_syllable_csv(Path(args.input) if args.input else None,
                                       Path(args.output) if args.output else None)
    elif args.type == "traditional":
        output = generate_traditional_csv(Path(args.input) if args.input else None,
                                          Path(args.output) if args.output else None)
    elif args.type == "origin":
        output = generate_llm_classification_csv(Path(args.input) if args.input else None,
                                                 "origin",
                                                 Path(args.output) if args.output else None)
    elif args.type == "sound_character":
        output = generate_llm_classification_csv(Path(args.input) if args.input else None,
                                                 "sound_character",
                                                 Path(args.output) if args.output else None)
    elif args.type == "international":
        output = generate_llm_classification_csv(Path(args.input) if args.input else None,
                                                 "international",
                                                 Path(args.output) if args.output else None)

    print(f"\nDone. Output: {output}")
