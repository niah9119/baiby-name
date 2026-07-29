"""Tests for the Denmark (DST) dataset fetch.

Local-first: an existing download is reused and the network is left alone.
"""

from unittest import mock

import pytest
from requests.exceptions import RequestException

from pipeline.fetch_dst import (
    DST_AJAX_URL,
    DST_MAX_YEAR,
    DST_MIN_YEAR,
    DENMARK_COUNTRY_CODE,
    _endpoint_payload,
    _local_filename,
    _year_suffix,
    fetch_dst_range,
    fetch_dst_year,
    local_dataset,
)


class TestYearSuffix:
    """Test the year suffix calculation."""

    def test_suffix_one_for_1985_1999(self):
        """Years 1985-1999 use suffix _1."""
        assert _year_suffix(1985) == "_1"
        assert _year_suffix(1999) == "_1"

    def test_suffix_two_for_2000_2025(self):
        """Years 2000-2025 use suffix _2."""
        assert _year_suffix(2000) == "_2"
        assert _year_suffix(2024) == "_2"
        assert _year_suffix(2025) == "_2"

    def test_out_of_range_raises(self):
        """Years outside the range raise ValueError."""
        with pytest.raises(ValueError, match="outside supported range"):
            _year_suffix(1984)
        with pytest.raises(ValueError, match="outside supported range"):
            _year_suffix(2026)


class TestEndpointPayload:
    """Test the endpoint payload construction."""

    def test_payload_for_1999_uses_suffix_1(self):
        """1999 should use suffix _1."""
        payload = _endpoint_payload(1999)
        assert payload == {"p1": "1999_1"}

    def test_payload_for_2000_uses_suffix_2(self):
        """2000 should use suffix _2."""
        payload = _endpoint_payload(2000)
        assert payload == {"p1": "2000_2"}

    def test_payload_for_2024_uses_suffix_2(self):
        """2024 should use suffix _2."""
        payload = _endpoint_payload(2024)
        assert payload == {"p1": "2024_2"}


class TestLocalFilename:
    """Test the filename generation."""

    def test_filename_format(self):
        """Check filename follows the expected pattern."""
        assert _local_filename(2024) == "dst-2024.html"
        assert _local_filename(1985) == "dst-1985.html"


class TestLocalDataset:
    """Test the local dataset detection."""

    def test_absent_when_not_downloaded(self, tmp_path):
        """No file exists returns None."""
        assert local_dataset(year=2024, output_dir=tmp_path) is None

    def test_found_when_present(self, tmp_path):
        """Existing file is returned."""
        (tmp_path / "dst-2024.html").write_text("content")
        result = local_dataset(year=2024, output_dir=tmp_path)
        assert result == tmp_path / "dst-2024.html"

    def test_empty_file_does_not_count(self, tmp_path):
        """Empty files are not considered valid."""
        (tmp_path / "dst-2024.html").write_text("")
        assert local_dataset(year=2024, output_dir=tmp_path) is None

    def test_any_year_checks_all_years(self, tmp_path):
        """With year=None, checks all years and returns first found."""
        (tmp_path / "dst-2020.html").write_text("content")
        result = local_dataset(year=None, output_dir=tmp_path)
        assert result == tmp_path / "dst-2020.html"

    def test_any_year_returns_none_when_empty(self, tmp_path):
        """With year=None and no files, returns None."""
        assert local_dataset(year=None, output_dir=tmp_path) is None


class TestFetchYear:
    """Test the fetch_dst_year function."""

    def test_reuses_local_copy_without_network(self, tmp_path):
        """Existing local copy is reused."""
        existing = tmp_path / "dst-2024.html"
        existing.write_text("existing content")

        with mock.patch("pipeline.fetch_dst.requests.post") as post:
            result = fetch_dst_year(year=2024, output_dir=tmp_path)

        post.assert_not_called()
        assert result == existing

    def test_downloads_when_no_local_copy(self, tmp_path):
        """Downloads when no local copy exists."""
        mock_response = mock.Mock()
        mock_response.text = "<table><caption>Pigenavne</caption><tr><td>1</td><td>Emma</td><td>100</td><td>5</td></tr></table>"
        mock_response.raise_for_status = mock.Mock()

        with mock.patch("pipeline.fetch_dst.requests.post", return_value=mock_response) as post:
            result = fetch_dst_year(year=2024, output_dir=tmp_path)

        post.assert_called_once()
        assert post.call_args[0][0] == DST_AJAX_URL
        assert post.call_args[1]["data"] == {"p1": "2024_2"}
        assert result.exists()

    def test_force_redownloads_over_local_copy(self, tmp_path):
        """Force parameter causes re-download."""
        (tmp_path / "dst-2024.html").write_text("old content")

        mock_response = mock.Mock()
        mock_response.text = "<table><caption>Pigenavne</caption><tr><td>1</td><td>Emma</td><td>100</td><td>5</td></tr></table>"
        mock_response.raise_for_status = mock.Mock()

        with mock.patch("pipeline.fetch_dst.requests.post", return_value=mock_response) as post:
            fetch_dst_year(year=2024, output_dir=tmp_path, force=True)

        post.assert_called_once()

    def test_network_failure_is_surfaced(self, tmp_path):
        """Network errors are propagated."""
        with mock.patch(
            "pipeline.fetch_dst.requests.post", side_effect=Exception("Network error")
        ):
            with pytest.raises(Exception, match="Network error"):
                fetch_dst_year(year=2024, output_dir=tmp_path)

    def test_empty_response_is_rejected(self, tmp_path):
        """Empty or invalid responses are rejected."""
        mock_response = mock.Mock()
        mock_response.text = "<html><body>No tables here</body></html>"
        mock_response.raise_for_status = mock.Mock()

        with mock.patch("pipeline.fetch_dst.requests.post", return_value=mock_response):
            with pytest.raises(Exception, match="empty or invalid"):
                fetch_dst_year(year=2024, output_dir=tmp_path)

        # File should not be created
        assert not (tmp_path / "dst-2024.html").exists()

    def test_user_agent_header_is_set(self, tmp_path):
        """The User-Agent header is included in requests."""
        mock_response = mock.Mock()
        mock_response.text = "<table><caption>Pigenavne</caption><tr><td>1</td><td>Emma</td><td>100</td><td>5</td></tr></table>"
        mock_response.raise_for_status = mock.Mock()

        with mock.patch("pipeline.fetch_dst.requests.post", return_value=mock_response) as post:
            fetch_dst_year(year=2024, output_dir=tmp_path)

        headers = post.call_args[1].get("headers", {})
        assert "User-Agent" in headers
        assert "Mozilla/5.0" in headers["User-Agent"]


class TestFetchRange:
    """Test the fetch_dst_range function."""

    def test_fetches_range_of_years(self, tmp_path):
        """Fetches all years in the range."""
        mock_response = mock.Mock()
        mock_response.text = "<table><caption>Pigenavne</caption><tr><td>1</td><td>Emma</td><td>100</td><td>5</td></tr></table>"
        mock_response.raise_for_status = mock.Mock()

        with mock.patch("pipeline.fetch_dst.requests.post", return_value=mock_response):
            results = fetch_dst_range(2020, 2022, output_dir=tmp_path)

        assert len(results) == 3
        assert (tmp_path / "dst-2020.html").exists()
        assert (tmp_path / "dst-2021.html").exists()
        assert (tmp_path / "dst-2022.html").exists()

    def test_skips_failed_years(self, tmp_path):
        """Continues fetching other years even if one fails (with RequestException)."""
        mock_response = mock.Mock()
        mock_response.text = "<table><caption>Pigenavne</caption><tr><td>1</td><td>Emma</td><td>100</td><td>5</td></tr></table>"
        mock_response.raise_for_status = mock.Mock()

        def side_effect(*args, **kwargs):
            data = kwargs.get("data", {})
            year = data.get("p1", "")
            if "2021" in year:
                raise RequestException("Network error")
            return mock_response

        with mock.patch("pipeline.fetch_dst.requests.post", side_effect=side_effect):
            results = fetch_dst_range(2020, 2022, output_dir=tmp_path)

        # fetch_dst_range prints a warning but continues
        assert len(results) == 2  # Only 2020 and 2022 succeeded
        assert (tmp_path / "dst-2020.html").exists()
        assert not (tmp_path / "dst-2021.html").exists()
        assert (tmp_path / "dst-2022.html").exists()
