"""Tests for the import pipeline modules."""

import csv
import os
import subprocess
import tempfile
import uuid
from pathlib import Path
from unittest import mock

import pandas as pd
import pytest

from pipeline.config import CANONICAL_CSV_PATH, SSA_DATA_DIR, USA_COUNTRY_CODE
from pipeline.fetch import _is_valid_ssa_content, fetch_ssa_year
from pipeline.load import _name_stat_exists, load_canonical_csv
from pipeline.normalize import _extract_year_from_filename, normalize_ssa_file


class TestNormalize:
    """Tests for the normalize module."""

    def test_extract_year_from_filename_valid(self):
        """Test extracting year from valid SSA filename."""
        assert _extract_year_from_filename("yob2023.txt") == 2023
        assert _extract_year_from_filename("yob1880.txt") == 1880
        assert _extract_year_from_filename("yob2000.txt") == 2000

    def test_extract_year_from_filename_invalid(self):
        """Test extracting year from invalid filename raises error."""
        with pytest.raises(ValueError, match="Could not extract year"):
            _extract_year_from_filename("invalid.txt")
        with pytest.raises(ValueError, match="Could not extract year"):
            _extract_year_from_filename("yob.txt")
        with pytest.raises(ValueError, match="Could not extract year"):
            _extract_year_from_filename("yob2023.csv")

    def test_normalize_ssa_file_basic(self):
        """Test normalizing a basic SSA file."""
        with tempfile.TemporaryDirectory() as tmpdir:
            # Create a test SSA file
            ssa_file = Path(tmpdir) / "yob2023.txt"
            ssa_file.write_text("Alice,F,1000\nBob,M,1500\nCharlie,F,500\n")

            # Normalize it
            df = normalize_ssa_file(ssa_file)

            # Check structure
            assert list(df.columns) == ["name", "country", "sex", "year", "count", "rank"]
            assert len(df) == 3

            # Check values
            assert df["country"].iloc[0] == USA_COUNTRY_CODE
            assert df["year"].iloc[0] == 2023
            assert df["name"].iloc[0] == "Alice"
            assert df["sex"].iloc[0] == "F"
            assert df["count"].iloc[0] == 1000

            # Check rank (Alice should be rank 1 for F)
            assert df["rank"].iloc[0] == 1

    def test_normalize_ssa_file_ranking(self):
        """Test that ranking works correctly within sex groups."""
        with tempfile.TemporaryDirectory() as tmpdir:
            ssa_file = Path(tmpdir) / "yob2023.txt"
            ssa_file.write_text(
                "Alice,F,3000\n"
                "Bob,M,2000\n"
                "Charlie,F,2000\n"
                "Diana,F,2500\n"
                "Eve,F,1000\n"
            )

            df = normalize_ssa_file(ssa_file)

            # Sort by count to verify ranking
            females = df[df["sex"] == "F"].sort_values("count", ascending=False)

            # Alice (3000) should be rank 1
            assert females[females["name"] == "Alice"]["rank"].iloc[0] == 1

            # Diana (2500) should be rank 2
            assert females[females["name"] == "Diana"]["rank"].iloc[0] == 2

            # Charlie and Eve both have 2000 and 1000 respectively, sequential ranks
            assert females[females["name"] == "Charlie"]["rank"].iloc[0] == 3
            assert females[females["name"] == "Eve"]["rank"].iloc[0] == 4

    def test_normalize_ssa_file_tied_ranking(self):
        """Test that tied ranks work correctly within sex groups."""
        with tempfile.TemporaryDirectory() as tmpdir:
            ssa_file = Path(tmpdir) / "yob2023.txt"
            ssa_file.write_text(
                "Alice,F,3000\n"
                "Bob,M,2000\n"
                "Charlie,F,2000\n"
                "Diana,F,2000\n"  # Tied with Charlie
                "Eve,F,1000\n"
            )

            df = normalize_ssa_file(ssa_file)

            # Sort by count to verify ranking
            females = df[df["sex"] == "F"].sort_values("count", ascending=False)

            # Alice (3000) should be rank 1
            assert females[females["name"] == "Alice"]["rank"].iloc[0] == 1

            # Charlie and Diana both have 2000, should both get rank 2 (min method)
            # (Alice took rank 1, next rank is 2 for the tied group)
            assert females[females["name"] == "Charlie"]["rank"].iloc[0] == 2
            assert females[females["name"] == "Diana"]["rank"].iloc[0] == 2

            # Eve should be rank 4 (next after tie)
            assert females[females["name"] == "Eve"]["rank"].iloc[0] == 4


class TestFetch:
    """Tests for the fetch module."""

    def test_is_valid_ssa_content_valid(self):
        """Test valid SSA content detection."""
        valid_content = "Alice,F,1000\nBob,M,1500\nCharlie,F,500\n"
        assert _is_valid_ssa_content(valid_content, 2023) is True

    def test_is_valid_ssa_content_invalid(self):
        """Test invalid content detection."""
        # Empty content
        assert _is_valid_ssa_content("", 2023) is False

        # Wrong format
        assert _is_valid_ssa_content("not a valid format", 2023) is False

        # Missing fields
        assert _is_valid_ssa_content("Alice,F\n", 2023) is False


class TestLoad:
    """Tests for the load module."""

    def test_name_stat_exists_check(self, tmp_path):
        """Test the idempotency check for existing name_stat records.

        This test verifies that loading the same CSV twice does not duplicate rows.
        We use a temporary SQLite database for this test.
        """
        import sqlalchemy
        from sqlalchemy import text

        # Create a temporary CSV file
        csv_path = tmp_path / "names_canonical.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["name", "country", "sex", "year", "count", "rank"])
            writer.writerow(["TestName", "US", "F", "2023", "100", "1"])

        # Create a temporary database file
        db_file = tmp_path / "test.db"
        db_url = f"sqlite:///{db_file}"

        # Create the database schema
        engine = sqlalchemy.create_engine(db_url)
        with engine.connect() as conn:
            conn.execute(text('''
                CREATE TABLE IF NOT EXISTS given_name (
                    id INTEGER PRIMARY KEY,
                    name TEXT UNIQUE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            '''))
            conn.execute(text('''
                CREATE TABLE IF NOT EXISTS country (
                    id INTEGER PRIMARY KEY,
                    code TEXT UNIQUE NOT NULL,
                    name TEXT NOT NULL
                )
            '''))
            conn.execute(text('''
                CREATE TABLE IF NOT EXISTS name_stat (
                    id INTEGER PRIMARY KEY,
                    given_name_id INTEGER NOT NULL,
                    country_id INTEGER NOT NULL,
                    sex TEXT NOT NULL,
                    year INTEGER NOT NULL,
                    count INTEGER NOT NULL,
                    rank INTEGER NOT NULL,
                    UNIQUE(given_name_id, country_id, sex, year),
                    FOREIGN KEY (given_name_id) REFERENCES given_name(id),
                    FOREIGN KEY (country_id) REFERENCES country(id)
                )
            '''))
            conn.commit()

        # First load
        stats1 = load_canonical_csv(csv_path=csv_path, db_url=db_url)
        assert stats1["inserted_rows"] == 1
        assert stats1["total_rows"] == 1

        # Second load (should skip since already exists)
        stats2 = load_canonical_csv(csv_path=csv_path, db_url=db_url)
        assert stats2["inserted_rows"] == 0
        assert stats2["skipped_rows"] == 1
        assert stats2["total_rows"] == 1

        # Verify data in database
        engine = sqlalchemy.create_engine(db_url)
        with engine.connect() as conn:
            result = conn.execute(text("SELECT COUNT(*) FROM name_stat"))
            count = result.scalar()
            assert count == 1


@pytest.fixture
def sample_csv_file(tmp_path):
    """Create a sample canonical CSV file."""
    csv_path = tmp_path / "names_canonical.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["name", "country", "sex", "year", "count", "rank"])
        writer.writerow(["TestName", "US", "F", "2023", "100", "1"])
    return csv_path


class TestIntegration:
    """Integration tests that require fixtures."""

    @pytest.fixture
    def sample_ssa_file(self, tmp_path):
        """Create a sample SSA data file."""
        ssa_file = tmp_path / "yob2023.txt"
        ssa_file.write_text(
            "Alice,F,3000\n"
            "Bob,M,2500\n"
            "Charlie,F,2000\n"
            "Diana,F,1500\n"
            "Eve,M,1000\n"
        )
        return ssa_file

    def test_full_normalize_flow(self, sample_ssa_file):
        """Test the full normalization flow."""
        df = normalize_ssa_file(sample_ssa_file)

        assert len(df) == 5
        assert df["country"].iloc[0] == USA_COUNTRY_CODE
        assert df["year"].iloc[0] == 2023
        assert set(df["sex"].unique()) == {"F", "M"}

    @pytest.mark.integration
    def test_load_integration(self, sample_csv_file, tmp_path):
        """Test the full load flow with a real database using Testcontainers."""
        import sqlalchemy
        from sqlalchemy import text

        from testcontainers.community.postgres import PostgresContainer

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Create the database schema (load.py expects tables to exist)
            engine = sqlalchemy.create_engine(db_url)
            with engine.connect() as conn:
                conn.execute(text('''
                    CREATE TABLE IF NOT EXISTS given_name (
                        id SERIAL PRIMARY KEY,
                        name TEXT UNIQUE NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                '''))
                conn.execute(text('''
                    CREATE TABLE IF NOT EXISTS country (
                        id SERIAL PRIMARY KEY,
                        code TEXT UNIQUE NOT NULL,
                        name TEXT NOT NULL
                    )
                '''))
                conn.execute(text('''
                    CREATE TABLE IF NOT EXISTS name_stat (
                        id SERIAL PRIMARY KEY,
                        given_name_id INTEGER NOT NULL,
                        country_id INTEGER NOT NULL,
                        sex TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        count INTEGER NOT NULL,
                        rank INTEGER NOT NULL,
                        UNIQUE(given_name_id, country_id, sex, year),
                        FOREIGN KEY (given_name_id) REFERENCES given_name(id),
                        FOREIGN KEY (country_id) REFERENCES country(id)
                    )
                '''))
                conn.commit()

            # First load
            stats1 = load_canonical_csv(csv_path=sample_csv_file, db_url=db_url)
            assert stats1["inserted_rows"] == 1
            assert stats1["total_rows"] == 1

            # Second load (should skip since already exists)
            stats2 = load_canonical_csv(csv_path=sample_csv_file, db_url=db_url)
            assert stats2["inserted_rows"] == 0
            assert stats2["skipped_rows"] == 1
            assert stats2["total_rows"] == 1

            # Verify data in database
            engine = sqlalchemy.create_engine(db_url)
            with engine.connect() as conn:
                result = conn.execute(
                    text("SELECT COUNT(*) FROM name_stat WHERE rank = 1")
                )
                count = result.scalar()
                assert count == 1

    def test_full_pipeline_flow(self, tmp_path):
        """Test the full pipeline flow end-to-end with sample data."""
        import subprocess

        # Create a sample SSA file
        ssa_dir = tmp_path / "ssa"
        ssa_dir.mkdir()
        ssa_file = ssa_dir / "yob2023.txt"
        ssa_file.write_text("Alice,F,1000\nBob,M,1500\n")

        # Create a temp output directory
        output_dir = tmp_path / "output"
        output_dir.mkdir()

        # Normalize
        result = subprocess.run(
            [
                ".venv/bin/python3",
                "-m",
                "pipeline.normalize",
                "--input-dir",
                str(ssa_dir),
                "--output",
                str(output_dir / "names_canonical.csv"),
            ],
            capture_output=True,
            text=True,
            cwd="/work/git/baiby-name/pipeline",
        )
        assert result.returncode == 0, f"normalize failed: {result.stderr}"

        # Check output exists and has expected data
        output_file = output_dir / "names_canonical.csv"
        assert output_file.exists()

        # Verify CSV content
        with open(output_file) as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            assert len(rows) == 2

        # Bob (1500) should have rank 1, Alice (1000) should have rank 1 (for F)
        # Output is sorted by country, sex, year, rank
        assert rows[0]["name"] == "Alice"  # Alice comes first alphabetically
        assert rows[0]["rank"] == "1"
        assert rows[0]["sex"] == "F"
        assert rows[0]["count"] == "1000"

        assert rows[1]["name"] == "Bob"
        assert rows[1]["rank"] == "1"
        assert rows[1]["sex"] == "M"
        assert rows[1]["count"] == "1500"
