"""Tests for the Denmark (DST) normalizer.

Tests the HTML parsing and normalization to canonical CSV format.
"""

import pytest

from pipeline.normalize_dst import (
    DENMARK_COUNTRY_CODE,
    _parse_dst_html,
    normalize_dst_file,
    normalize_dst_directory,
)


class TestParseDstHtml:
    """Test the HTML parsing function."""

    def test_parses_girls_table(self):
        """Parses a girls table correctly."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Emma</td><td>445</td><td>16</td></tr>
                <tr><td>2</td><td>Ella</td><td>437</td><td>16</td></tr>
            </tbody>
        </table>
        """
        records = _parse_dst_html(html, 2024)

        assert len(records) == 2
        assert records[0] == {"name": "Emma", "sex": "Girl", "year": 2024, "count": 445, "rank": 1}
        assert records[1] == {"name": "Ella", "sex": "Girl", "year": 2024, "count": 437, "rank": 2}

    def test_parses_boys_table(self):
        """Parses a boys table correctly."""
        html = """
        <table class="table">
            <caption class="names__headerName">Drengenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Oscar</td><td>450</td><td>16</td></tr>
                <tr><td>2</td><td>Emil</td><td>420</td><td>15</td></tr>
            </tbody>
        </table>
        """
        records = _parse_dst_html(html, 2024)

        assert len(records) == 2
        assert records[0] == {"name": "Oscar", "sex": "Boy", "year": 2024, "count": 450, "rank": 1}
        assert records[1] == {"name": "Emil", "sex": "Boy", "year": 2024, "count": 420, "rank": 2}

    def test_parses_both_tables(self):
        """Parses both girls and boys tables."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Emma</td><td>445</td><td>16</td></tr>
            </tbody>
        </table>
        <table class="table">
            <caption class="names__headerName">Drengenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Oscar</td><td>450</td><td>16</td></tr>
            </tbody>
        </table>
        """
        records = _parse_dst_html(html, 2024)

        assert len(records) == 2
        assert any(r["name"] == "Emma" and r["sex"] == "Girl" for r in records)
        assert any(r["name"] == "Oscar" and r["sex"] == "Boy" for r in records)

    def test_skips_header_rows(self):
        """Header rows with th elements are skipped."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Emma</td><td>445</td><td>16</td></tr>
            </tbody>
        </table>
        """
        records = _parse_dst_html(html, 2024)

        # Should only have the data row, not the header
        assert len(records) == 1
        assert records[0]["name"] == "Emma"

    def test_danish_characters_preserved(self):
        """Danish characters (æ, ø, å) survive parsing."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Søren</td><td>100</td><td>5</td></tr>
                <tr><td>2</td><td>Åse</td><td>95</td><td>5</td></tr>
                <tr><td>3</td><td>Peter</td><td>90</td><td>5</td></tr>
            </tbody>
        </table>
        """
        records = _parse_dst_html(html, 2024)

        names = [r["name"] for r in records]
        assert "Søren" in names
        assert "Åse" in names
        assert "Peter" in names

    def test_tied_names(self):
        """Handles tied names with same rank."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Emma</td><td>445</td><td>16</td></tr>
                <tr><td>2</td><td>Frida</td><td>413</td><td>14</td></tr>
                <tr><td>2</td><td>Ella</td><td>413</td><td>14</td></tr>
            </tbody>
        </table>
        """
        records = _parse_dst_html(html, 2024)

        assert records[0]["name"] == "Emma"
        assert records[1]["name"] == "Frida"
        assert records[1]["rank"] == 2
        assert records[2]["name"] == "Ella"
        assert records[2]["rank"] == 2

    def test_empty_table_returns_empty(self):
        """Empty table returns empty list."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
        </table>
        """
        records = _parse_dst_html(html, 2024)

        assert len(records) == 0

    def test_ignores_unknown_table_type(self):
        """Tables without recognized captions are ignored."""
        html = """
        <table class="table">
            <caption class="names__headerName">Other Data</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Other</td><td>100</td><td>5</td></tr>
            </tbody>
        </table>
        """
        records = _parse_dst_html(html, 2024)

        assert len(records) == 0


class TestNormalizeDstFile:
    """Test the file normalization function."""

    def test_normalizes_html_file(self, tmp_path):
        """Normalizes a single HTML file correctly."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Emma</td><td>445</td><td>16</td></tr>
            </tbody>
        </table>
        """
        file_path = tmp_path / "dst-2024.html"
        file_path.write_text(html, encoding="utf-8")

        from pipeline.normalize_dst import normalize_dst_file

        df = normalize_dst_file(file_path)

        assert len(df) == 1
        assert df["name"].iloc[0] == "Emma"
        assert df["sex"].iloc[0] == "Girl"
        assert df["year"].iloc[0] == 2024
        assert df["count"].iloc[0] == 445
        assert df["rank"].iloc[0] == 1
        assert df["country"].iloc[0] == DENMARK_COUNTRY_CODE

    def test_extracts_year_from_filename(self, tmp_path):
        """Extracts year from filename."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Emma</td><td>445</td><td>16</td></tr>
            </tbody>
        </table>
        """
        file_path = tmp_path / "dst-1995.html"
        file_path.write_text(html, encoding="utf-8")

        df = normalize_dst_file(file_path)
        assert df["year"].iloc[0] == 1995

    def test_invalid_filename_raises(self, tmp_path):
        """Invalid filename raises ValueError."""
        file_path = tmp_path / "invalid.html"
        file_path.write_text("content")

        with pytest.raises(ValueError, match="Cannot extract year"):
            normalize_dst_file(file_path)

    def test_danish_characters_round_trip(self, tmp_path):
        """Danish characters survive the round trip."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Søren</td><td>100</td><td>5</td></tr>
            </tbody>
        </table>
        """
        file_path = tmp_path / "dst-2024.html"
        file_path.write_text(html, encoding="utf-8")

        df = normalize_dst_file(file_path)
        assert df["name"].iloc[0] == "Søren"

    def test_columns_in_correct_order(self, tmp_path):
        """Columns are in the correct order."""
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Emma</td><td>445</td><td>16</td></tr>
            </tbody>
        </table>
        """
        file_path = tmp_path / "dst-2024.html"
        file_path.write_text(html, encoding="utf-8")

        df = normalize_dst_file(file_path)
        assert list(df.columns) == ["name", "country", "sex", "year", "count", "rank"]


class TestNormalizeDstDirectory:
    """Test the directory normalization function."""

    def test_normalizes_multiple_files(self, tmp_path):
        """Normalizes all HTML files in directory."""
        for year in [2020, 2021, 2022]:
            html = f"""
            <table class="table">
                <caption class="names__headerName">Pigenavne</caption>
                <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
                <tbody>
                    <tr><td>1</td><td>Name{year}</td><td>100</td><td>5</td></tr>
                </tbody>
            </table>
            """
            (tmp_path / f"dst-{year}.html").write_text(html, encoding="utf-8")

        df = normalize_dst_directory(tmp_path)

        assert len(df) == 3
        years = set(df["year"].unique())
        assert years == {2020, 2021, 2022}

    def test_empty_directory_raises(self, tmp_path):
        """Empty directory raises ValueError."""
        with pytest.raises(ValueError, match="No DST HTML files found"):
            normalize_dst_directory(tmp_path)

    def test_sorted_output(self, tmp_path):
        """Output is sorted by country, sex, year, rank."""
        # Create files in reverse order
        for year in [2022, 2020, 2021]:
            html = f"""
            <table class="table">
                <caption class="names__headerName">Pigenavne</caption>
                <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
                <tbody>
                    <tr><td>1</td><td>Name{year}</td><td>100</td><td>5</td></tr>
                </tbody>
            </table>
            """
            (tmp_path / f"dst-{year}.html").write_text(html, encoding="utf-8")

        df = normalize_dst_directory(tmp_path)

        # Check years are in order
        assert list(df["year"].unique()) == [2020, 2021, 2022]


class TestEndToEnd:
    """End-to-end tests for DST normalization."""

    def test_full_directory_processing(self, tmp_path):
        """Process all files in directory."""
        # Create a realistic 2024 HTML (top 50 per sex = 100 rows)
        html_2024 = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Emma</td><td>445</td><td>16</td></tr>
                <tr><td>2</td><td>Ella</td><td>437</td><td>16</td></tr>
            </tbody>
        </table>
        <table class="table">
            <caption class="names__headerName">Drengenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Oscar</td><td>450</td><td>16</td></tr>
                <tr><td>2</td><td>Emil</td><td>420</td><td>15</td></tr>
            </tbody>
        </table>
        """
        (tmp_path / "dst-2024.html").write_text(html_2024, encoding="utf-8")

        # Create a 1992 HTML (top 25 per sex = 50 rows)
        html_1992 = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Camilla</td><td>1408</td><td>45</td></tr>
                <tr><td>2</td><td>Louise</td><td>950</td><td>30</td></tr>
            </tbody>
        </table>
        <table class="table">
            <caption class="names__headerName">Drengenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Jonas</td><td>1500</td><td>48</td></tr>
                <tr><td>2</td><td>Malte</td><td>1200</td><td>38</td></tr>
            </tbody>
        </table>
        """
        (tmp_path / "dst-1992.html").write_text(html_1992, encoding="utf-8")

        df = normalize_dst_directory(tmp_path)

        # 4 girls + 4 boys = 8 rows
        assert len(df) == 8

        # Check 2024 data
        assert len(df[df["year"] == 2024]) == 4
        assert df[(df["year"] == 2024) & (df["name"] == "Emma")]["rank"].iloc[0] == 1

        # Check 1992 data
        assert len(df[df["year"] == 1992]) == 4
        assert df[(df["year"] == 1992) & (df["name"] == "Camilla")]["rank"].iloc[0] == 1

        # All have DK country code
        assert set(df["country"].unique()) == {"DK"}

    def test_known_names_2024(self, tmp_path):
        """Verify known 2024 names are correctly parsed."""
        # Emma is #1 girls in 2024, Oscar is #1 boys
        html = """
        <table class="table">
            <caption class="names__headerName">Pigenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Emma</td><td>445</td><td>16</td></tr>
            </tbody>
        </table>
        <table class="table">
            <caption class="names__headerName">Drengenavne</caption>
            <thead><tr><th>Nr</th><th>Navn</th><th>Antal</th><th>Pr. 1 000</th></tr></thead>
            <tbody>
                <tr><td>1</td><td>Oscar</td><td>450</td><td>16</td></tr>
            </tbody>
        </table>
        """
        (tmp_path / "dst-2024.html").write_text(html, encoding="utf-8")

        df = normalize_dst_directory(tmp_path)

        # Emma should be rank 1 among girls
        girl_rows = df[(df["name"] == "Emma") & (df["sex"] == "Girl")]
        assert len(girl_rows) == 1
        assert girl_rows["rank"].iloc[0] == 1

        # Oscar should be rank 1 among boys
        boy_rows = df[(df["name"] == "Oscar") & (df["sex"] == "Boy")]
        assert len(boy_rows) == 1
        assert boy_rows["rank"].iloc[0] == 1
