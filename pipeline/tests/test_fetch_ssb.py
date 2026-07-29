"""Tests for the Norway (SSB) dataset fetch.

Local-first: an existing download is reused and the network is left alone. The guards
matter because SSB answers with 200 and a short JSON body on some malformed queries,
which would otherwise be saved as if it were the full series.
"""

import json
from unittest import mock

import pytest
from requests.exceptions import RequestException

from pipeline.fetch_ssb import (
    MIN_PLAUSIBLE_SIZE,
    SSB_DATA_FILENAME,
    SSB_QUERY,
    SSB_TABLE_URL,
    download_ssb_dataset,
    local_dataset,
)


def _ok_response():
    payload = {"value": [1], "dimension": {}, "pad": "x" * MIN_PLAUSIBLE_SIZE}
    body = json.dumps(payload).encode()
    r = mock.Mock()
    r.content = body
    r.json = mock.Mock(return_value=payload)
    r.raise_for_status = mock.Mock()
    return r


class TestLocalDataset:
    def test_absent_when_not_downloaded(self, tmp_path):
        assert local_dataset(tmp_path) is None

    def test_found_when_present(self, tmp_path):
        (tmp_path / SSB_DATA_FILENAME).write_text("{}")
        assert local_dataset(tmp_path) == tmp_path / SSB_DATA_FILENAME

    def test_empty_file_does_not_count(self, tmp_path):
        (tmp_path / SSB_DATA_FILENAME).write_text("")
        assert local_dataset(tmp_path) is None


class TestQuery:
    def test_query_leaves_names_and_years_unfiltered(self):
        """Selecting only ContentsCode is what returns the whole series in one call."""
        codes = [q["code"] for q in SSB_QUERY["query"]]
        assert codes == ["ContentsCode"]
        assert SSB_QUERY["response"]["format"] == "json-stat2"


class TestDownload:
    def test_reuses_local_copy_without_network(self, tmp_path):
        existing = tmp_path / SSB_DATA_FILENAME
        existing.write_text('{"already": "here"}')

        with mock.patch("pipeline.fetch_ssb.requests.post") as post:
            result = download_ssb_dataset(output_dir=tmp_path)

        post.assert_not_called()
        assert result == existing

    def test_downloads_when_nothing_local(self, tmp_path):
        with mock.patch(
            "pipeline.fetch_ssb.requests.post", return_value=_ok_response()
        ) as post:
            result = download_ssb_dataset(output_dir=tmp_path)

        post.assert_called_once()
        assert post.call_args[0][0] == SSB_TABLE_URL
        assert post.call_args[1]["json"] == SSB_QUERY
        assert result.exists()

    def test_force_redownloads_over_local_copy(self, tmp_path):
        (tmp_path / SSB_DATA_FILENAME).write_text('{"stale": true}')

        with mock.patch(
            "pipeline.fetch_ssb.requests.post", return_value=_ok_response()
        ) as post:
            download_ssb_dataset(output_dir=tmp_path, force=True)

        post.assert_called_once()

    def test_truncated_body_is_rejected(self, tmp_path):
        short = mock.Mock()
        short.content = b'{"value":[]}'
        short.raise_for_status = mock.Mock()

        with mock.patch("pipeline.fetch_ssb.requests.post", return_value=short):
            with pytest.raises(RequestException, match="too small"):
                download_ssb_dataset(output_dir=tmp_path)

        assert not (tmp_path / SSB_DATA_FILENAME).exists()

    def test_payload_without_json_stat_keys_is_rejected(self, tmp_path):
        wrong = mock.Mock()
        payload = {"unexpected": "shape", "pad": "x" * MIN_PLAUSIBLE_SIZE}
        wrong.content = json.dumps(payload).encode()
        wrong.json = mock.Mock(return_value=payload)
        wrong.raise_for_status = mock.Mock()

        with mock.patch("pipeline.fetch_ssb.requests.post", return_value=wrong):
            with pytest.raises(RequestException, match="json-stat2"):
                download_ssb_dataset(output_dir=tmp_path)

    def test_network_failure_is_surfaced(self, tmp_path):
        with mock.patch(
            "pipeline.fetch_ssb.requests.post", side_effect=RequestException("boom")
        ):
            with pytest.raises(RequestException, match="Could not download"):
                download_ssb_dataset(output_dir=tmp_path)
