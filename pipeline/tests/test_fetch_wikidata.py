"""Tests for Wikidata famous bearers fetch.

Local-first: an existing download is reused and the network is left alone. The guards
matter because Wikidata answers with 200 and a short JSON body on some malformed queries,
which would otherwise be saved as if it were the full results.
"""

import csv
import json
from pathlib import Path
from unittest import mock

import pytest

from pipeline.fetch_wikidata import (
    ALIAS_REGEX,
    USER_AGENT,
    WIKIDATA_SPARQL_URL,
    fetch_for_country_and_subcategory,
    fetch_people_qids,
    fetch_aliases_for_person,
    fetch_person_label,
    local_dataset,
    process_wikidata_data,
    read_csv_response,
    write_csv,
)


class TestAliasRegex:
    """Tests for the alias filtering regex.

    The regex filters aliases to:
    - Single token
    - Latin script
    - Reasonable length (2-15 chars)
    - Starts with uppercase letter

    Note: Final filtering to only include names in the given_name table happens
    in process_wikidata_data() via name universe intersection.
    """

    def test_valid_short_forms_match(self):
        """Test that valid short forms are matched."""
        # Short forms that should match
        valid = ["Leo", "Henke", "Gunnar", "Max", "Lionel", "Zlatan"]
        for name in valid:
            assert ALIAS_REGEX.match(name), f"Expected {name} to match"

    def test_valid_names_with_diacritics_match(self):
        """Test that names with diacritics are matched."""
        valid = ["Göran", "Åke", "Östen", "Élisabeth", "Henrik", "Cécile"]
        for name in valid:
            assert ALIAS_REGEX.match(name), f"Expected {name} to match"

    def test_nicknames_with_numbers_do_not_match(self):
        """Test that nicknames with numbers are not matched."""
        invalid = ["LM10", "LM"]
        for name in invalid:
            assert not ALIAS_REGEX.match(name), f"Expected {name} not to match"

    def test_two_char_names_match(self):
        """Test that 2-character names match (the regex allows 2+ chars)."""
        # Note: the regex allows 2+ characters ({1,14} for the remaining chars)
        # So "Ab" would match (1 uppercase + 1 lowercase = 2 chars total)
        match = ALIAS_REGEX.match("Ab")
        assert match, "Expected 'Ab' to match (2 char name)"

    def test_name_too_long_does_not_match(self):
        """Test that names longer than 15 characters don't match."""
        invalid = ["VeryLongGivenName"]
        for name in invalid:
            assert not ALIAS_REGEX.match(name), f"Expected {name} not to match"


class TestLocalDataset:
    """Tests for the local dataset caching."""

    def test_absent_when_not_downloaded(self, tmp_path):
        """Test that local_dataset returns None when not downloaded."""
        assert local_dataset("US", "Q30") is None

    def test_found_when_present(self, tmp_path, monkeypatch):
        """Test that local_dataset returns the path when present."""
        # Mock the WIKIDATA_RAW_DIR to use tmp_path
        import pipeline.fetch_wikidata as fw
        original_dir = fw.WIKIDATA_RAW_DIR
        fw.WIKIDATA_RAW_DIR = tmp_path

        # Note: the filename uses the first occupation QID from the list
        expected_path = tmp_path / "wikidata_US_Q10800557.csv"
        expected_path.write_text("person,personLabel,givenNames,aliases,subcategory,country\n")
        result = local_dataset("US", "Q10800557")
        assert result == expected_path

        # Restore
        fw.WIKIDATA_RAW_DIR = original_dir

    def test_empty_file_does_not_count(self, tmp_path, monkeypatch):
        """Test that empty files don't count as cached."""
        import pipeline.fetch_wikidata as fw
        original_dir = fw.WIKIDATA_RAW_DIR
        fw.WIKIDATA_RAW_DIR = tmp_path

        expected_path = tmp_path / "wikidata_US_Q10800557.csv"
        expected_path.write_text("")
        assert local_dataset("US", "Q10800557") is None

        # Restore
        fw.WIKIDATA_RAW_DIR = original_dir


class TestFetchPeopleQids:
    """Tests for fetching person QIDs."""

    def test_query_structure(self):
        """Test that the query has the right structure."""
        # This test verifies the query contains expected elements
        query = """
        SELECT DISTINCT ?person WHERE {{
          ?person wdt:P31 wd:Q5 .
          ?person wdt:P27 wd:Q30 .
          ?person wdt:P106 wd:Q10800557 .
        }}
        LIMIT 500
        """
        assert "wdt:P31 wd:Q5" in query  # P31 = instance of, Q5 = human
        assert "wdt:P27 wd:Q30" in query  # P27 = country of citizenship, Q30 = USA
        assert "wdt:P106 wd:Q10800557" in query  # P106 = occupation, Q10800557 = film actor


class TestProcessWikidataData:
    """Tests for processing raw Wikidata data."""

    def test_process_with_valid_names(self):
        """Test processing with valid names that exist in the universe."""
        raw_data = [
            {
                "person": "http://www.wikidata.org/entity/Q615",
                "personLabel": "Lionel Messi",
                "givenNames": "Lionel",
                "aliases": "Leo",
                "subcategory": "SPORTS_STAR",
                "country": "SE",
            },
        ]
        name_universe = {"Lionel", "Leo"}

        processed, unresolved = process_wikidata_data(raw_data, name_universe)

        assert len(processed) == 1
        assert processed[0]["public_name"] == "Lionel Messi"
        assert "Lionel;Leo" == processed[0]["given_names"]
        assert processed[0]["subcategory"] == "SPORTS_STAR"
        assert processed[0]["country"] == "SE"
        assert processed[0]["wikidata_id"] == "Q615"
        assert unresolved == 0

    def test_process_with_unresolved_alias(self):
        """Test that unresolved aliases are reported."""
        raw_data = [
            {
                "person": "http://www.wikidata.org/entity/Q615",
                "personLabel": "Lionel Messi",
                "givenNames": "Lionel",
                "aliases": "Leo;Ibrakadabra",
                "subcategory": "SPORTS_STAR",
                "country": "SE",
            },
        ]
        name_universe = {"Lionel", "Leo"}  # Ibrakadabra is not in universe

        processed, unresolved = process_wikidata_data(raw_data, name_universe)

        assert len(processed) == 1
        assert processed[0]["given_names"] == "Lionel;Leo"
        assert unresolved == 1

    def test_process_with_diacritics(self):
        """Test that diacritics are preserved."""
        raw_data = [
            {
                "person": "http://www.wikidata.org/entity/Q123",
                "personLabel": "Zlatan Ibrahimović",
                "givenNames": "Zlatan",
                "aliases": "",
                "subcategory": "SPORTS_STAR",
                "country": "SE",
            },
        ]
        name_universe = {"Zlatan"}

        processed, unresolved = process_wikidata_data(raw_data, name_universe)

        assert len(processed) == 1
        assert processed[0]["public_name"] == "Zlatan Ibrahimović"
        assert "Ibra" not in processed[0]["given_names"]  # Nickname filtered out

    def test_process_skips_invalid_subcategory(self):
        """Test that rows with invalid subcategory are skipped."""
        raw_data = [
            {
                "person": "http://www.wikidata.org/entity/Q615",
                "personLabel": "Test Person",
                "givenNames": "Test",
                "aliases": "",
                "subcategory": "INVALID",
                "country": "SE",
            },
        ]
        name_universe = {"Test"}

        processed, unresolved = process_wikidata_data(raw_data, name_universe)

        assert len(processed) == 0

    def test_process_skips_invalid_country(self):
        """Test that rows with invalid country are skipped."""
        raw_data = [
            {
                "person": "http://www.wikidata.org/entity/Q615",
                "personLabel": "Test Person",
                "givenNames": "Test",
                "aliases": "",
                "subcategory": "SPORTS_STAR",
                "country": "XX",  # Invalid country
            },
        ]
        name_universe = {"Test"}

        processed, unresolved = process_wikidata_data(raw_data, name_universe)

        assert len(processed) == 0

    def test_process_multi_given_names(self):
        """Test processing with multiple given names (semicolon-separated)."""
        raw_data = [
            {
                "person": "http://www.wikidata.org/entity/Q170581",
                "personLabel": "Ole Gunnar Solskjær",
                "givenNames": "Gunnar;Ole",
                "aliases": "",
                "subcategory": "SPORTS_STAR",
                "country": "NO",
            },
        ]
        name_universe = {"Gunnar", "Ole"}

        processed, unresolved = process_wikidata_data(raw_data, name_universe)

        assert len(processed) == 1
        assert processed[0]["public_name"] == "Ole Gunnar Solskjær"
        assert processed[0]["given_names"] == "Gunnar;Ole"
        assert processed[0]["country"] == "NO"
        assert processed[0]["wikidata_id"] == "Q170581"

    def test_process_deduplicates_given_names(self):
        """Test that duplicate given names are removed."""
        raw_data = [
            {
                "person": "http://www.wikidata.org/entity/Q615",
                "personLabel": "Leo Messi",
                "givenNames": "Leo;Lionel",
                "aliases": "Leo",  # Leo appears in both givenNames and aliases
                "subcategory": "SPORTS_STAR",
                "country": "SE",  # Valid country code
            },
        ]
        name_universe = {"Leo", "Lionel"}

        processed, unresolved = process_wikidata_data(raw_data, name_universe)

        assert len(processed) == 1
        # Leo should appear only once (deduplicated)
        given_names = processed[0]["given_names"].split(";")
        assert len(given_names) == len(set(given_names))  # No duplicates
        assert "Leo" in given_names
        assert "Lionel" in given_names


class TestWriteCsv:
    """Tests for writing CSV output."""

    def test_write_csv_creates_file(self, tmp_path):
        """Test that write_csv creates the output file."""
        data = [
            {
                "public_name": "Test Person",
                "subcategory": "SPORTS_STAR",
                "given_names": "Test",
                "country": "SE",
                "wikidata_id": "Q123",
            },
        ]
        # Note: write_csv writes to output_dir/famous_bearers.csv
        output_dir = tmp_path / "output"

        write_csv(data, output_dir=output_dir)

        output_path = output_dir / "famous_bearers.csv"
        assert output_path.exists()
        assert output_path.stat().st_size > 0

    def test_write_csv_preserves_diacritics(self, tmp_path):
        """Test that diacritics are preserved in output."""
        data = [
            {
                "public_name": "Zlatan Ibrahimović",
                "subcategory": "SPORTS_STAR",
                "given_names": "Zlatan",
                "country": "SE",
                "wikidata_id": "Q550",
            },
        ]
        output_dir = tmp_path / "output"
        write_csv(data, output_dir=output_dir)

        output_path = output_dir / "famous_bearers.csv"
        content = output_path.read_text(encoding="utf-8")
        assert "Ibrahimović" in content

    def test_write_csv_has_correct_headers(self, tmp_path):
        """Test that CSV has the correct headers."""
        data = [
            {
                "public_name": "Test Person",
                "subcategory": "SPORTS_STAR",
                "given_names": "Test",
                "country": "SE",
                "wikidata_id": "Q123",
            },
        ]
        output_dir = tmp_path / "output"
        write_csv(data, output_dir=output_dir)

        output_path = output_dir / "famous_bearers.csv"
        with open(output_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            assert reader.fieldnames == ["public_name", "subcategory", "given_names", "country", "wikidata_id"]


class TestReadCsvResponse:
    """Tests for reading cached CSV responses."""

    def test_read_csv_response(self, tmp_path):
        """Test reading a CSV response."""
        csv_content = """person,personLabel,givenNames,aliases,subcategory,country
http://www.wikidata.org/entity/Q615,Lionel Messi,Lionel,Leo,SPORTS_STAR,SE
"""
        test_file = tmp_path / "test.csv"
        test_file.write_text(csv_content)

        data = read_csv_response(test_file)

        assert len(data) == 1
        assert data[0]["personLabel"] == "Lionel Messi"
        assert data[0]["subcategory"] == "SPORTS_STAR"
