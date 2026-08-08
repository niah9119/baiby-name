"""Tests for the import pipeline modules."""

import csv
import os
import subprocess
import tempfile
import uuid
from pathlib import Path
from unittest import mock

import pandas as pd
import sqlalchemy
from sqlalchemy import text
import pytest

from pipeline.config import CANONICAL_CSV_PATH, SSA_DATA_DIR, USA_COUNTRY_CODE
from pipeline.fetch import _is_valid_ssa_content, fetch_ssa_year
from pipeline.load import load_canonical_csv
from pipeline.normalize import _extract_year_from_filename, normalize_ssa_file


def _load_flyway_schema(engine):
    """Load the Flyway V2 and V7 migrations into the database."""
    migration_dir = Path(__file__).resolve().parent.parent.parent / "src" / "main" / "resources" / "db" / "migration"

    # Load V2 schema
    v2_path = migration_dir / "V2__core_schema.sql"
    v2_sql = v2_path.read_text()

    # Load V7 migration (adds wikidata_id column)
    v7_path = migration_dir / "V7__famous_bearer_wikidata_id.sql"
    v7_sql = v7_path.read_text()

    with engine.connect() as conn:
        conn.execute(text(v2_sql))
        conn.execute(text(v7_sql))
        conn.commit()


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

            # Check values - SSA M/F should be normalized to Boy/Girl
            assert df["country"].iloc[0] == USA_COUNTRY_CODE
            assert df["year"].iloc[0] == 2023
            assert df["name"].iloc[0] == "Alice"
            assert df["sex"].iloc[0] == "Girl"
            assert df["count"].iloc[0] == 1000

            # Check rank (Alice should be rank 1 for Girl)
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
            # Note: SSA M/F normalized to Boy/Girl
            girls = df[df["sex"] == "Girl"].sort_values("count", ascending=False)
            boys = df[df["sex"] == "Boy"].sort_values("count", ascending=False)

            # Alice (3000) should be rank 1
            assert girls[girls["name"] == "Alice"]["rank"].iloc[0] == 1

            # Diana (2500) should be rank 2
            assert girls[girls["name"] == "Diana"]["rank"].iloc[0] == 2

            # Charlie and Eve both have 2000 and 1000 respectively, sequential ranks
            assert girls[girls["name"] == "Charlie"]["rank"].iloc[0] == 3
            assert girls[girls["name"] == "Eve"]["rank"].iloc[0] == 4

            # Bob (2000) should be rank 1 for boys
            assert boys[boys["name"] == "Bob"]["rank"].iloc[0] == 1

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
            # Note: SSA M/F normalized to Boy/Girl
            girls = df[df["sex"] == "Girl"].sort_values("count", ascending=False)

            # Alice (3000) should be rank 1
            assert girls[girls["name"] == "Alice"]["rank"].iloc[0] == 1

            # Charlie and Diana both have 2000, should both get rank 2 (min method)
            # (Alice took rank 1, next rank is 2 for the tied group)
            assert girls[girls["name"] == "Charlie"]["rank"].iloc[0] == 2
            assert girls[girls["name"] == "Diana"]["rank"].iloc[0] == 2

            # Eve should be rank 4 (next after tie)
            assert girls[girls["name"] == "Eve"]["rank"].iloc[0] == 4

            # Bob (2000) should be rank 1 for boys
            boys = df[df["sex"] == "Boy"].sort_values("count", ascending=False)
            assert boys[boys["name"] == "Bob"]["rank"].iloc[0] == 1


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
        Uses PostgresContainer for consistency with other integration tests.
        """
        import sqlalchemy
        from sqlalchemy import text

        from testcontainers.community.postgres import PostgresContainer

        # Create a temporary CSV file with canonical sex values
        csv_path = tmp_path / "names_canonical.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["name", "country", "sex", "year", "count", "rank"])
            writer.writerow(["TestName", "US", "Girl", "2023", "100", "1"])

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Load the Flyway schema
            engine = sqlalchemy.create_engine(db_url)
            _load_flyway_schema(engine)

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

    def test_source_duplicate_rows(self, tmp_path):
        """Test that duplicate rows in the CSV are reported separately.

        This test verifies that if the CSV contains duplicate rows (same name/country/sex/year),
        the loader correctly reports:
        - inserted_rows: actual rows inserted into the database
        - dropped_on_conflict: rows that were silently dropped due to conflict
        - skipped_rows: rows that already existed before the load

        Totals should reconcile: inserted + dropped + skipped == total_rows
        Uses PostgresContainer with the Flyway schema for consistency with production.
        """
        import sqlalchemy
        from sqlalchemy import text

        from testcontainers.community.postgres import PostgresContainer

        # Create a temporary CSV file with a duplicate row
        # Use canonical sex values (Boy/Girl instead of M/F)
        csv_path = tmp_path / "names_canonical.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["name", "country", "sex", "year", "count", "rank"])
            writer.writerow(["Alice", "US", "Girl", "2023", "100", "1"])
            writer.writerow(["Bob", "US", "Boy", "2023", "50", "2"])
            writer.writerow(["Alice", "US", "Girl", "2023", "100", "1"])  # Duplicate of row 1
            writer.writerow(["Charlie", "US", "Girl", "2023", "75", "3"])

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Load the Flyway schema
            engine = sqlalchemy.create_engine(db_url)
            _load_flyway_schema(engine)

            # Load the CSV
            stats = load_canonical_csv(csv_path=csv_path, db_url=db_url)

            # Verify totals reconcile: inserted + dropped + skipped == total_rows
            assert stats["total_rows"] == 4
            assert stats["inserted_rows"] == 3  # Alice, Bob, Charlie (Alice duplicate dropped)
            assert stats["dropped_on_conflict"] == 1  # Second Alice row
            assert stats["skipped_rows"] == 0  # No pre-existing rows

            # Verify the DB has exactly 3 rows (duplicates not inserted)
            engine = sqlalchemy.create_engine(db_url)
            with engine.connect() as conn:
                result = conn.execute(text("SELECT COUNT(*) FROM name_stat"))
                count = result.scalar()
                assert count == 3

    def test_rejects_invalid_sex_values(self, tmp_path):
        """Test that the loader rejects rows with invalid sex values."""
        import sqlalchemy
        from sqlalchemy import text

        from testcontainers.community.postgres import PostgresContainer

        # Create a temporary CSV file with an invalid sex value
        csv_path = tmp_path / "names_canonical.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["name", "country", "sex", "year", "count", "rank"])
            writer.writerow(["TestName", "US", "M", "2023", "100", "1"])  # Invalid - should be 'Boy'

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Load the Flyway schema
            engine = sqlalchemy.create_engine(db_url)
            _load_flyway_schema(engine)

            # Load the CSV - should raise ValueError
            with pytest.raises(ValueError, match="Invalid sex value 'M'"):
                load_canonical_csv(csv_path=csv_path, db_url=db_url)

    def test_load_famous_bearers_csv(self, tmp_path):
        """Test loading famous bearers CSV into the database.

        This test verifies:
        - Bearers are inserted with correct data
        - Links are created between names and bearers
        - Idempotency: running twice doesn't duplicate rows
        """
        import sqlalchemy
        from sqlalchemy import text

        from testcontainers.community.postgres import PostgresContainer

        # Create a temporary CSV file with famous bearers data
        csv_path = tmp_path / "famous_bearers.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["public_name", "subcategory", "given_names", "country", "wikidata_id"])
            writer.writerow(["Lionel Messi", "SPORTS_STAR", "Lionel;Leo", "AR", "Q1033"])
            writer.writerow(["Zlatan Ibrahimović", "SPORTS_STAR", "Zlatan", "SE", "Q550"])

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Load the Flyway schema
            engine = sqlalchemy.create_engine(db_url)
            _load_flyway_schema(engine)

            # Create the given names that will be linked
            with engine.connect() as conn:
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Lionel', CURRENT_TIMESTAMP)"))
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Leo', CURRENT_TIMESTAMP)"))
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Zlatan', CURRENT_TIMESTAMP)"))
                conn.commit()

            # First load
            stats1 = load_canonical_csv(csv_path=csv_path, db_url=db_url)

            assert stats1["total_rows"] == 2
            assert stats1["inserted_bearers"] == 2
            assert stats1["skipped_bearers"] == 0
            assert stats1["inserted_links"] == 3  # Lionel;Leo + Zlatan = 3 links
            assert stats1["skipped_links"] == 0
            assert stats1["unresolved_names"] == []

            # Verify data in database
            with engine.connect() as conn:
                # Check famous_bearer count
                result = conn.execute(text("SELECT COUNT(*) FROM famous_bearer"))
                assert result.scalar() == 2

                # Check name_famous_bearer count
                result = conn.execute(text("SELECT COUNT(*) FROM name_famous_bearer"))
                assert result.scalar() == 3

                # Verify the bearers were created correctly
                result = conn.execute(text("SELECT public_name, subcategory FROM famous_bearer ORDER BY id"))
                rows = result.fetchall()
                assert len(rows) == 2
                assert ("Lionel Messi", "SPORTS_STAR") in rows
                assert ("Zlatan Ibrahimović", "SPORTS_STAR") in rows

                # Verify links exist
                result = conn.execute(text("""
                    SELECT gn.name, fb.public_name
                    FROM name_famous_bearer nfb
                    JOIN given_name gn ON gn.id = nfb.given_name_id
                    JOIN famous_bearer fb ON fb.id = nfb.famous_bearer_id
                    ORDER BY gn.name, fb.public_name
                """))
                links = result.fetchall()
                assert ("Lionel", "Lionel Messi") in links
                assert ("Leo", "Lionel Messi") in links
                assert ("Zlatan", "Zlatan Ibrahimović") in links

            # Second load (should skip all since already exists)
            stats2 = load_canonical_csv(csv_path=csv_path, db_url=db_url)

            assert stats2["total_rows"] == 2
            assert stats2["inserted_bearers"] == 0
            assert stats2["skipped_bearers"] == 2
            assert stats2["inserted_links"] == 0
            assert stats2["skipped_links"] == 3

            # Verify DB hasn't changed
            with engine.connect() as conn:
                result = conn.execute(text("SELECT COUNT(*) FROM famous_bearer"))
                assert result.scalar() == 2
                result = conn.execute(text("SELECT COUNT(*) FROM name_famous_bearer"))
                assert result.scalar() == 3

    def test_load_famous_bearers_unresolved_names(self, tmp_path):
        """Test that unresolved names cause the load to fail and no bearers are inserted.

        This is a transactional load: if any name cannot be resolved, no bearers
        or links are inserted, and the database remains unchanged.
        """
        import sqlalchemy
        from sqlalchemy import text

        from testcontainers.community.postgres import PostgresContainer

        # Create a temporary CSV file with famous bearers
        csv_path = tmp_path / "famous_bearers.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["public_name", "subcategory", "given_names", "country", "wikidata_id"])
            # Create the names that exist
            writer.writerow(["Lionel Messi", "SPORTS_STAR", "Lionel;Leo", "AR", "Q1033"])
            # These names don't exist
            writer.writerow(["Not A Person", "SPORTS_STAR", "Fake;Unknown;Unfound", "US", "Q999999"])

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Load the Flyway schema
            engine = sqlalchemy.create_engine(db_url)
            _load_flyway_schema(engine)

            # Create the names that exist in the name universe
            with engine.connect() as conn:
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Lionel', CURRENT_TIMESTAMP)"))
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Leo', CURRENT_TIMESTAMP)"))
                conn.commit()

            # Load the CSV
            stats = load_canonical_csv(csv_path=csv_path, db_url=db_url)

            # Load should detect unresolved names
            assert stats["total_rows"] == 2
            assert stats["inserted_bearers"] == 2  # Counted but not inserted
            # Unresolved names should be sorted alphabetically
            assert sorted(stats["unresolved_names"]) == ["Fake", "Unfound", "Unknown"]

            # Verify NO bearers or links were inserted (transaction was rolled back)
            with engine.connect() as conn:
                result = conn.execute(text("SELECT COUNT(*) FROM famous_bearer"))
                assert result.scalar() == 0
                result = conn.execute(text("SELECT COUNT(*) FROM name_famous_bearer"))
                assert result.scalar() == 0

    def test_load_famous_bearers_multi_name_bearer(self, tmp_path):
        """Test that bearers with multiple given names link to all of them.

        Real rows from the committed CSV:
        - Ole Gunnar Solskjær -> Gunnar;Ole (NO)
        - Max von Sydow -> Max;Adolf (SE)
        """
        import sqlalchemy
        from sqlalchemy import text

        from testcontainers.community.postgres import PostgresContainer

        # Create a temporary CSV file with multi-name bearers
        csv_path = tmp_path / "famous_bearers.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["public_name", "subcategory", "given_names", "country", "wikidata_id"])
            # Use names that exist in the given_name table
            writer.writerow(["Ole Gunnar Solskjær", "SPORTS_STAR", "Gunnar;Ole", "NO", "Q18976"])
            writer.writerow(["Max von Sydow", "MOVIE_STAR", "Max;Adolf", "SE", "Q203215"])

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Load the Flyway schema
            engine = sqlalchemy.create_engine(db_url)
            _load_flyway_schema(engine)

            # Create the given names that will be linked
            with engine.connect() as conn:
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Gunnar', CURRENT_TIMESTAMP)"))
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Ole', CURRENT_TIMESTAMP)"))
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Max', CURRENT_TIMESTAMP)"))
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Adolf', CURRENT_TIMESTAMP)"))
                conn.commit()

            # First load
            stats = load_canonical_csv(csv_path=csv_path, db_url=db_url)

            assert stats["total_rows"] == 2
            assert stats["inserted_bearers"] == 2
            assert stats["inserted_links"] == 4  # Gunnar;Ole + Max;Adolf = 4 links
            assert stats["skipped_links"] == 0
            assert stats["unresolved_names"] == []

            # Verify both links exist in name_famous_bearer
            with engine.connect() as conn:
                result = conn.execute(text("""
                    SELECT gn.name, fb.public_name
                    FROM name_famous_bearer nfb
                    JOIN given_name gn ON gn.id = nfb.given_name_id
                    JOIN famous_bearer fb ON fb.id = nfb.famous_bearer_id
                    ORDER BY gn.name, fb.public_name
                """))
                links = result.fetchall()

                # Both names should be linked to Ole Gunnar Solskjær
                assert ("Gunnar", "Ole Gunnar Solskjær") in links
                assert ("Ole", "Ole Gunnar Solskjær") in links

                # Both names should be linked to Max von Sydow
                assert ("Max", "Max von Sydow") in links
                assert ("Adolf", "Max von Sydow") in links


class TestLoadCli:
    """Tests for the load.py CLI entry point."""

    def test_load_cli_famous_bearers_success(self, tmp_path):
        """Test CLI load with famous bearers CSV that has all names resolved."""
        import subprocess
        import sys

        from testcontainers.community.postgres import PostgresContainer

        # Create a temporary CSV file with famous bearers data
        csv_path = tmp_path / "famous_bearers.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["public_name", "subcategory", "given_names", "country", "wikidata_id"])
            writer.writerow(["Lionel Messi", "SPORTS_STAR", "Lionel", "AR", "Q1033"])

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Load the Flyway schema
            engine = sqlalchemy.create_engine(db_url)
            _load_flyway_schema(engine)

            # Create the given names that will be linked
            with engine.connect() as conn:
                conn.execute(text("INSERT INTO given_name (name, created_at) VALUES ('Lionel', CURRENT_TIMESTAMP)"))
                conn.commit()

            # Run the CLI
            pipeline_dir = Path(__file__).resolve().parent.parent
            result = subprocess.run(
                [
                    sys.executable,
                    "-m",
                    "pipeline.load",
                    "--csv",
                    str(csv_path),
                    "--db-url",
                    db_url,
                ],
                capture_output=True,
                text=True,
                cwd=str(pipeline_dir),
            )

            assert result.returncode == 0, f"CLI failed: {result.stderr}"
            assert "Inserted bearers: 1" in result.stdout
            assert "Inserted links: 1" in result.stdout

    def test_load_cli_famous_bearers_unresolved_names_fail(self, tmp_path):
        """Test CLI load fails when there are unresolved names and no bearers are inserted."""
        import subprocess
        import sys
        from sqlalchemy import text

        from testcontainers.community.postgres import PostgresContainer

        # Create a temporary CSV file with famous bearers data
        csv_path = tmp_path / "famous_bearers.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["public_name", "subcategory", "given_names", "country", "wikidata_id"])
            # Use a name that doesn't exist in the database
            writer.writerow(["Some Person", "SPORTS_STAR", "UnknownName;AnotherFake", "US", "Q999999"])

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Load the Flyway schema
            engine = sqlalchemy.create_engine(db_url)
            _load_flyway_schema(engine)

            # Verify database is empty before load
            with engine.connect() as conn:
                result = conn.execute(text("SELECT COUNT(*) FROM famous_bearer"))
                assert result.scalar() == 0
                result = conn.execute(text("SELECT COUNT(*) FROM name_famous_bearer"))
                assert result.scalar() == 0

            # Run the CLI - should fail with exit code 1
            pipeline_dir = Path(__file__).resolve().parent.parent
            result = subprocess.run(
                [
                    sys.executable,
                    "-m",
                    "pipeline.load",
                    "--csv",
                    str(csv_path),
                    "--db-url",
                    db_url,
                ],
                capture_output=True,
                text=True,
                cwd=str(pipeline_dir),
            )

            # CLI should fail because names are unresolved
            assert result.returncode == 1, f"CLI should have failed but got exit code {result.returncode}"
            assert "Unresolved names: 2" in result.stdout
            assert "ERROR" in result.stdout
            assert "could not be resolved" in result.stdout

            # Verify NO bearers or links were inserted (transaction was rolled back)
            with engine.connect() as conn:
                result = conn.execute(text("SELECT COUNT(*) FROM famous_bearer"))
                assert result.scalar() == 0
                result = conn.execute(text("SELECT COUNT(*) FROM name_famous_bearer"))
                assert result.scalar() == 0


@pytest.fixture
def sample_csv_file(tmp_path):
    """Create a sample canonical CSV file."""
    csv_path = tmp_path / "names_canonical.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["name", "country", "sex", "year", "count", "rank"])
        writer.writerow(["TestName", "US", "Girl", "2023", "100", "1"])
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
        # SSA M/F normalized to Boy/Girl
        assert set(df["sex"].unique()) == {"Boy", "Girl"}

    @pytest.mark.integration
    def test_load_integration(self, sample_csv_file, tmp_path):
        """Test the full load flow with a real database using Testcontainers."""
        import sqlalchemy
        from sqlalchemy import text

        from testcontainers.community.postgres import PostgresContainer

        # Start a PostgreSQL container for testing
        with PostgresContainer("postgres:15-alpine") as postgres:
            db_url = postgres.get_connection_url()

            # Load the Flyway schema instead of hand-rolled DDL
            engine = sqlalchemy.create_engine(db_url)
            _load_flyway_schema(engine)

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
        import sys
        from pathlib import Path

        # Get the pipeline directory dynamically
        pipeline_dir = Path(__file__).resolve().parent.parent

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
                sys.executable,
                "-m",
                "pipeline.normalize",
                "--input-dir",
                str(ssa_dir),
                "--output",
                str(output_dir / "names_canonical.csv"),
            ],
            capture_output=True,
            text=True,
            cwd=str(pipeline_dir),
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

        # Output is sorted by country, sex, year, rank
        # Note: SSA M/F normalized to Boy/Girl
        # Since "Boy" > "Girl" alphabetically, Bob comes first
        assert rows[0]["name"] == "Bob"
        assert rows[0]["rank"] == "1"
        assert rows[0]["sex"] == "Boy"
        assert rows[0]["count"] == "1500"

        assert rows[1]["name"] == "Alice"
        assert rows[1]["rank"] == "1"
        assert rows[1]["sex"] == "Girl"
        assert rows[1]["count"] == "1000"
