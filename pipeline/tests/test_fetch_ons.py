"""Tests for the England and Wales (ONS) dataset fetch.

Local-first: an existing download is reused and the network is left alone. The guards
matter because ONS answers with 200 and a short HTML body on some malformed queries,
which would otherwise be saved as if it were the full workbook.
"""

import os
from unittest import mock

import pytest
from requests.exceptions import RequestException

from pipeline.fetch_ons import (
    MIN_PLAUSIBLE_SIZE,
    ONS_WORKBOOK_FILENAME,
    ONS_WORKBOOK_URL,
    XLSX_CONTENT_TYPE,
    download_ons_archive,
    local_workbook,
)


def _response(content=b"x" * MIN_PLAUSIBLE_SIZE, content_type=XLSX_CONTENT_TYPE):
    r = mock.Mock()
    r.content = content
    r.headers = {"Content-Type": content_type}
    r.raise_for_status = mock.Mock()
    return r


class TestLocalWorkbook:
    def test_absent_when_not_downloaded(self, tmp_path):
        assert local_workbook(tmp_path) is None

    def test_found_when_present(self, tmp_path):
        (tmp_path / ONS_WORKBOOK_FILENAME).write_bytes(b"data")
        assert local_workbook(tmp_path) == tmp_path / ONS_WORKBOOK_FILENAME

    def test_empty_file_does_not_count(self, tmp_path):
        (tmp_path / ONS_WORKBOOK_FILENAME).write_bytes(b"")
        assert local_workbook(tmp_path) is None


class TestDownload:
    def test_reuses_local_copy_without_network(self, tmp_path):
        existing = tmp_path / ONS_WORKBOOK_FILENAME
        existing.write_bytes(b"already here")

        with mock.patch("pipeline.fetch_ons.requests.get") as get:
            result = download_ons_archive(output_dir=tmp_path)

        get.assert_not_called()
        assert result == existing
        assert existing.read_bytes() == b"already here"

    def test_downloads_when_nothing_local(self, tmp_path):
        with mock.patch("pipeline.fetch_ons.requests.get", return_value=_response()) as get:
            result = download_ons_archive(output_dir=tmp_path)

        get.assert_called_once()
        assert get.call_args[0][0] == ONS_WORKBOOK_URL
        assert result.exists()
        assert result.stat().st_size == MIN_PLAUSIBLE_SIZE

    def test_force_redownloads_over_local_copy(self, tmp_path):
        (tmp_path / ONS_WORKBOOK_FILENAME).write_bytes(b"stale")

        with mock.patch("pipeline.fetch_ons.requests.get", return_value=_response()) as get:
            result = download_ons_archive(output_dir=tmp_path, force=True)

        get.assert_called_once()
        assert result.read_bytes() != b"stale"

    def test_html_error_page_is_rejected(self, tmp_path):
        """ONS moving the file would serve HTML; that must not be saved as a workbook."""
        html = _response(content=b"<html>not found</html>" * 9000, content_type="text/html")

        with mock.patch("pipeline.fetch_ons.requests.get", return_value=html):
            with pytest.raises(RequestException, match="Content-Type"):
                download_ons_archive(output_dir=tmp_path)

        assert not (tmp_path / ONS_WORKBOOK_FILENAME).exists()

    def test_truncated_body_is_rejected(self, tmp_path):
        tiny = _response(content=b"too small")

        with mock.patch("pipeline.fetch_ons.requests.get", return_value=tiny):
            with pytest.raises(RequestException, match="too small"):
                download_ons_archive(output_dir=tmp_path)

        assert not (tmp_path / ONS_WORKBOOK_FILENAME).exists()

    def test_network_failure_is_surfaced(self, tmp_path):
        with mock.patch(
            "pipeline.fetch_ons.requests.get", side_effect=RequestException("boom")
        ):
            with pytest.raises(RequestException, match="Could not download"):
                download_ons_archive(output_dir=tmp_path)
